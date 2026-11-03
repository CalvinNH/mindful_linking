#!/usr/bin/env python3
"""
Icon generation and proportion refinement script for Mindful Linking (Link Ledger).
Generates Bauhaus-aligned, proportion-refined app icons:
- Square master asset (design/app_icon.png) with Pale Lime background #DCEA95.
- Adaptive foreground asset (drawable-nodpi/ic_launcher_foreground.png) with transparent background.
- Density mipmaps (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) for square and circular cutouts.
- Adaptive icon XML definitions (mipmap-anydpi-v26) and background shape/color XMLs.
"""

import math
import os
from PIL import Image, ImageDraw

def extract_foreground_glyphs(src_img: Image.Image, bg_tolerance: float = 12.0):
    """
    Extracts the central logo glyphs from the master artwork with smooth alpha matting.
    Identifies non-background pixels and creates a clean RGBA transparent layer.
    """
    src_rgb = src_img.convert('RGB')
    w, h = src_rgb.size
    src_data = src_rgb.load()

    # 1. Sample edge pixels to determine background color
    edge_pixels = []
    for x in range(w):
        edge_pixels.append(src_data[x, 0])
        edge_pixels.append(src_data[x, h - 1])
    for y in range(h):
        edge_pixels.append(src_data[0, y])
        edge_pixels.append(src_data[w - 1, y])

    bg_r = sum(p[0] for p in edge_pixels) / len(edge_pixels)
    bg_g = sum(p[1] for p in edge_pixels) / len(edge_pixels)
    bg_b = sum(p[2] for p in edge_pixels) / len(edge_pixels)

    print(f"Detected background color: ({bg_r:.1f}, {bg_g:.1f}, {bg_b:.1f})")

    # 2. Extract foreground with alpha unmixing
    fg_img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    fg_data = fg_img.load()

    for y in range(h):
        for x in range(w):
            r, g, b = src_data[x, y]
            dist = math.sqrt((r - bg_r)**2 + (g - bg_g)**2 + (b - bg_b)**2)
            if dist < bg_tolerance:
                fg_data[x, y] = (0, 0, 0, 0)
            else:
                # Anti-aliasing transition range
                if dist < 40.0:
                    alpha_norm = (dist - bg_tolerance) / (40.0 - bg_tolerance)
                else:
                    alpha_norm = 1.0

                a = int(round(alpha_norm * 255))
                if a <= 0:
                    fg_data[x, y] = (0, 0, 0, 0)
                else:
                    alpha_f = max(0.05, alpha_norm)
                    unmixed_r = max(0, min(255, int(round(bg_r + (r - bg_r) / alpha_f))))
                    unmixed_g = max(0, min(255, int(round(bg_g + (g - bg_g) / alpha_f))))
                    unmixed_b = max(0, min(255, int(round(bg_b + (b - bg_b) / alpha_f))))
                    fg_data[x, y] = (unmixed_r, unmixed_g, unmixed_b, a)

    bbox = fg_img.getbbox()
    if not bbox:
        raise ValueError("Could not determine foreground glyph bounding box.")

    return fg_img, bbox

def generate_icons(target_width: int = 500):
    """
    Main icon generation pipeline.
    target_width: Desired width of the glyph on a 1024x1024 canvas (~500px corresponds to ~49-50% canvas, max radius <= 290px).
    """
    canvas_size = 1024
    pale_lime_hex = "#DCEA95"
    pale_lime_rgba = (220, 234, 149, 255) # RGB (220, 234, 149)

    # Master artwork search paths
    src_candidates = [
        'design/assets/mindful-linking-app-icon.png',
        'design/app_icon.png'
    ]
    master_path = None
    for cand in src_candidates:
        if os.path.exists(cand):
            master_path = cand
            break

    if not master_path:
        print(f"Error: None of the source candidates exist: {src_candidates}")
        return

    print(f"Loading master artwork: {master_path}")
    master_img = Image.open(master_path)
    print(f"Master image dimensions: {master_img.size} ({master_img.mode})")

    # Step 1: Identify exact glyph bounding box and extract transparent foreground
    fg_full, bbox = extract_foreground_glyphs(master_img)
    glyph_w = bbox[2] - bbox[0]
    glyph_h = bbox[3] - bbox[1]
    print(f"Exact logo glyph bounding box: {bbox} (width: {glyph_w}px, height: {glyph_h}px)")

    # Step 2: Proportional resize of the glyph
    cropped_glyph = fg_full.crop(bbox)
    scale = target_width / glyph_w
    target_height = int(round(glyph_h * scale))
    resized_glyph = cropped_glyph.resize((target_width, target_height), Image.Resampling.LANCZOS)
    print(f"Resized glyphs to {target_width}x{target_height} (occupies {target_width/canvas_size*100:.1f}% width, {target_height/canvas_size*100:.1f}% height)")

    # Step 3: Center glyphs on 1024x1024 canvas
    offset_x = (canvas_size - target_width) // 2
    offset_y = (canvas_size - target_height) // 2

    # A. Adaptive foreground layer (transparent background)
    foreground_canvas = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
    foreground_canvas.paste(resized_glyph, (offset_x, offset_y), resized_glyph)

    # B. Balanced square master asset (uniform Pale Lime #DCEA95 background)
    square_canvas = Image.new('RGBA', (canvas_size, canvas_size), pale_lime_rgba)
    square_canvas.alpha_composite(foreground_canvas)

    # C. Circular cutout (full-bleed circular mask on 1024x1024)
    circle_mask = Image.new('L', (canvas_size, canvas_size), 0)
    draw = ImageDraw.Draw(circle_mask)
    draw.ellipse((0, 0, canvas_size - 1, canvas_size - 1), fill=255)
    round_canvas = square_canvas.copy()
    round_canvas.putalpha(circle_mask)

    # Step 4: Verification and Metrics Calculation
    placed_bbox = foreground_canvas.getbbox()
    placed_cx = (placed_bbox[0] + placed_bbox[2]) / 2.0
    placed_cy = (placed_bbox[1] + placed_bbox[3]) / 2.0
    margin_left = placed_bbox[0]
    margin_right = canvas_size - placed_bbox[2]
    margin_top = placed_bbox[1]
    margin_bottom = canvas_size - placed_bbox[3]

    # Calculate maximum radius of non-transparent glyph pixels from center (512, 512)
    fg_pixels = foreground_canvas.load()
    max_radius = 0.0
    for y in range(canvas_size):
        for x in range(canvas_size):
            if fg_pixels[x, y][3] > 10:
                r = math.sqrt((x - canvas_size / 2.0)**2 + (y - canvas_size / 2.0)**2)
                if r > max_radius:
                    max_radius = r

    r_66dp = (66.0 / 108.0) * canvas_size / 2.0 # 312.89px
    r_72dp = (72.0 / 108.0) * canvas_size / 2.0 # 341.33px
    r_full_circle = canvas_size / 2.0 # 512.0px

    print("\n" + "="*50)
    print("PROPORTION & ALIGNMENT VERIFICATION METRICS")
    print("="*50)
    print(f"Canvas size: {canvas_size}x{canvas_size}")
    print(f"Placed glyph bounding box: {placed_bbox} ({placed_bbox[2]-placed_bbox[0]}x{placed_bbox[3]-placed_bbox[1]}px)")
    print(f"Center alignment: Glyph center=({placed_cx:.1f}, {placed_cy:.1f}), Canvas center=(512.0, 512.0)")
    print(f"Uniform margins: Left={margin_left}px, Right={margin_right}px, Top={margin_top}px, Bottom={margin_bottom}px")
    print(f"Max glyph radius from center: {max_radius:.2f}px (Target <= 290px)")
    print(f"Margin to 66dp inner circle safe zone (r={r_66dp:.2f}px): {r_66dp - max_radius:.2f}px")
    print(f"Margin to 72dp viewport safe zone (r={r_72dp:.2f}px): {r_72dp - max_radius:.2f}px")
    print(f"Margin to circular icon border (r={r_full_circle:.2f}px): {r_full_circle - max_radius:.2f}px")
    print("="*50 + "\n")

    # Step 5: Save design/app_icon.png
    os.makedirs('design', exist_ok=True)
    square_canvas.save('design/app_icon.png', 'PNG')
    print("Saved design/app_icon.png (1024x1024, Pale Lime background #DCEA95)")

    # Step 6: Save adaptive foreground to app/src/main/res/drawable-nodpi/ic_launcher_foreground.png
    res_dir = 'app/src/main/res'
    drawable_nodpi_dir = os.path.join(res_dir, 'drawable-nodpi')
    os.makedirs(drawable_nodpi_dir, exist_ok=True)
    foreground_canvas.save(os.path.join(drawable_nodpi_dir, 'ic_launcher_foreground.png'), 'PNG')
    print("Saved app/src/main/res/drawable-nodpi/ic_launcher_foreground.png (1024x1024, transparent background)")

    # Step 7: Ensure XML files
    # A. drawable/ic_launcher_background.xml
    drawable_dir = os.path.join(res_dir, 'drawable')
    os.makedirs(drawable_dir, exist_ok=True)
    bg_drawable_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="{pale_lime_hex}" />
</shape>
"""
    with open(os.path.join(drawable_dir, 'ic_launcher_background.xml'), 'w', encoding='utf-8') as f:
        f.write(bg_drawable_xml)

    # B. values/ic_launcher_background.xml
    values_dir = os.path.join(res_dir, 'values')
    os.makedirs(values_dir, exist_ok=True)
    bg_values_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">{pale_lime_hex}</color>
</resources>
"""
    with open(os.path.join(values_dir, 'ic_launcher_background.xml'), 'w', encoding='utf-8') as f:
        f.write(bg_values_xml)

    # C. mipmap-anydpi-v26/ic_launcher.xml and ic_launcher_round.xml
    anydpi_dir = os.path.join(res_dir, 'mipmap-anydpi-v26')
    os.makedirs(anydpi_dir, exist_ok=True)
    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
    with open(os.path.join(anydpi_dir, 'ic_launcher.xml'), 'w', encoding='utf-8') as f:
        f.write(adaptive_xml)
    with open(os.path.join(anydpi_dir, 'ic_launcher_round.xml'), 'w', encoding='utf-8') as f:
        f.write(adaptive_xml)
    print("Generated adaptive icon XMLs in mipmap-anydpi-v26 and background shape/values XMLs")

    # Step 8: Generate standard square and round icons across all density buckets
    densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }

    for folder, size in densities.items():
        folder_path = os.path.join(res_dir, folder)
        os.makedirs(folder_path, exist_ok=True)

        # Standard square launcher icon
        square_resized = square_canvas.resize((size, size), Image.Resampling.LANCZOS)
        square_resized.save(os.path.join(folder_path, 'ic_launcher.png'), 'PNG')

        # Circular cutout launcher icon
        round_resized = round_canvas.resize((size, size), Image.Resampling.LANCZOS)
        round_resized.save(os.path.join(folder_path, 'ic_launcher_round.png'), 'PNG')

        print(f"Generated {folder}: ic_launcher.png ({size}x{size}), ic_launcher_round.png ({size}x{size})")

    print("\nIcon proportion refinement and asset generation completed successfully.")

if __name__ == '__main__':
    generate_icons()
