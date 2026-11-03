package com.mindfullinking.domain.url

import com.mindfullinking.core.domain.UrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun testPrdRequiredTestCase1() {
        val input = "https://example.com/a?utm_source=x&x=1"
        val expected = "https://example.com/a?x=1"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testPrdRequiredTestCase2() {
        val input = "HTTPS://EXAMPLE.COM:443/a"
        val expected = "https://example.com/a"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testPrdRequiredTestCase3() {
        val input = "https://example.com/a?ref=homepage"
        val expected = "https://example.com/a?ref=homepage"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testPrdRequiredTestCase4() {
        val input = "https://example.com/a#section-2"
        val expected = "https://example.com/a#section-2"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testPrdRequiredTestCase5() {
        val input = "https://example.com/a?x=1&utm_campaign=test&y=2"
        val expected = "https://example.com/a?x=1&y=2"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testDefaultPortRemovalHttp() {
        val input = "http://example.com:80/path"
        val expected = "http://example.com/path"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testNonDefaultPortRetention() {
        val input = "http://example.com:8080/path"
        val expected = "http://example.com:8080/path"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testTrackingParamsStripping() {
        val input = "https://site.com/p?gclid=123&fbclid=456&igshid=789&_ga=1&_gl=2&msclkid=3&dclid=4&gbraid=5&wbraid=6&v=watch"
        val expected = "https://site.com/p?v=watch"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testTrailingQuestionMarkRemovedWhenAllParamsStripped() {
        val input = "https://example.com/page?utm_source=twitter&utm_medium=social"
        val expected = "https://example.com/page"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test
    fun testPunycodeHostLowercasing() {
        val input = "HTTP://BÜCHER.EXAMPLE.COM/PFAD"
        val expected = "http://xn--bcher-kva.example.com/PFAD"
        assertEquals(expected, UrlNormalizer.normalize(input))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectNonHttpScheme() {
        UrlNormalizer.normalize("ftp://example.com/file")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectUserCredentials() {
        UrlNormalizer.normalize("https://user:password@example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectMissingHost() {
        UrlNormalizer.normalize("https:///path")
    }
}
