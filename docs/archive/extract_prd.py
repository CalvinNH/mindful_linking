import zipfile
import xml.etree.ElementTree as ET

z = zipfile.ZipFile('MindfulLinking.docx')
xml_content = z.read('word/document.xml')
tree = ET.fromstring(xml_content)

# Extract all text nodes
text_parts = []
for node in tree.iter():
    if node.tag.endswith('t') and node.text:
        text_parts.append(node.text)
    elif node.tag.endswith('p'):
        text_parts.append('\n')

full_text = ''.join(text_parts)

with open('prd_content.txt', 'w', encoding='utf-8') as f:
    f.write(full_text)

print("Extracted successfully. Length:", len(full_text))
