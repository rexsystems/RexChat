package cc.rexsystems.rexChat.utils;

import cc.rexsystems.rexChat.RexChat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UpdateChecker version comparison logic
 */
class UpdateCheckerTest {

    @Mock
    private RexChat mockPlugin;
    
    @Mock
    private LogUtils mockLogUtils;

    private UpdateChecker updateChecker;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockPlugin.getLogUtils()).thenReturn(mockLogUtils);
        updateChecker = new UpdateChecker(mockPlugin);
    }

    @Test
    void testIsNewerVersion_SimpleVersions() throws Exception {
        assertTrue(invokeIsNewerVersion("1.2.0", "1.1.0"), "1.2.0 should be newer than 1.1.0");
        assertTrue(invokeIsNewerVersion("2.0.0", "1.9.9"), "2.0.0 should be newer than 1.9.9");
        assertFalse(invokeIsNewerVersion("1.1.0", "1.2.0"), "1.1.0 should not be newer than 1.2.0");
        assertFalse(invokeIsNewerVersion("1.5.0", "1.5.0"), "1.5.0 should not be newer than itself");
    }

    @Test
    void testIsNewerVersion_DifferentSegmentCounts() throws Exception {
        assertTrue(invokeIsNewerVersion("1.2.1", "1.2"), "1.2.1 should be newer than 1.2");
        assertTrue(invokeIsNewerVersion("1.2.0.1", "1.2.0"), "1.2.0.1 should be newer than 1.2.0");
        assertFalse(invokeIsNewerVersion("1.2", "1.2.1"), "1.2 should not be newer than 1.2.1");
        assertFalse(invokeIsNewerVersion("1.2.0", "1.2.0.0"), "1.2.0 should equal 1.2.0.0");
    }

    @Test
    void testIsNewerVersion_WithPrefixes() throws Exception {
        assertTrue(invokeIsNewerVersion("v1.2.0", "v1.1.0"), "v1.2.0 should be newer than v1.1.0");
        assertTrue(invokeIsNewerVersion("V2.0.0", "1.9.9"), "V2.0.0 should be newer than 1.9.9");
        assertFalse(invokeIsNewerVersion("v1.5.0", "V1.5.0"), "v1.5.0 should equal V1.5.0");
    }

    @Test
    void testIsNewerVersion_WithSuffixes() throws Exception {
        assertTrue(invokeIsNewerVersion("1.2.0-SNAPSHOT", "1.1.0"), "1.2.0-SNAPSHOT should be newer than 1.1.0");
        assertTrue(invokeIsNewerVersion("1.2.0", "1.1.0-RELEASE"), "1.2.0 should be newer than 1.1.0-RELEASE");
        assertFalse(invokeIsNewerVersion("1.5.0-BETA", "1.5.0-SNAPSHOT"), "1.5.0-BETA should equal 1.5.0-SNAPSHOT (both 1.5.0)");
    }

    @Test
    void testIsNewerVersion_WithNonNumericParts() throws Exception {
        assertTrue(invokeIsNewerVersion("1.2a", "1.1b"), "1.2a should be newer than 1.1b");
        assertFalse(invokeIsNewerVersion("1.5rc1", "1.5rc2"), "1.5rc1 should equal 1.5rc2 (both parse to 1.5)");
    }

    @Test
    void testIsNewerVersion_NullHandling() throws Exception {
        assertFalse(invokeIsNewerVersion(null, "1.0.0"), "null should not be newer than 1.0.0");
        assertFalse(invokeIsNewerVersion("1.0.0", null), "1.0.0 should not be newer than null");
        assertFalse(invokeIsNewerVersion(null, null), "null should not be newer than null");
    }

    @Test
    void testIsNewerVersion_EmptyStrings() throws Exception {
        assertFalse(invokeIsNewerVersion("", "1.0.0"), "Empty string should not be newer than 1.0.0");
        assertFalse(invokeIsNewerVersion("1.0.0", ""), "1.0.0 should not be newer than empty string");
    }

    @Test
    void testIsNewerVersion_MalformedVersions() throws Exception {
        // Should handle gracefully without throwing exceptions
        assertFalse(invokeIsNewerVersion("abc", "def"), "Malformed versions should not cause crashes");
        assertFalse(invokeIsNewerVersion("1.x.0", "1.0.0"), "1.x.0 should be handled gracefully");
    }

    @Test
    void testIsNewerVersion_LargeVersionNumbers() throws Exception {
        assertTrue(invokeIsNewerVersion("10.0.0", "9.9.9"), "10.0.0 should be newer than 9.9.9");
        assertTrue(invokeIsNewerVersion("1.100.0", "1.99.0"), "1.100.0 should be newer than 1.99.0");
    }

    @Test
    void testCleanVersionString() throws Exception {
        assertEquals("1.2.0", invokeCleanVersionString("v1.2.0"));
        assertEquals("1.2.0", invokeCleanVersionString("V1.2.0"));
        assertEquals("1.2.0", invokeCleanVersionString("1.2.0-SNAPSHOT"));
        assertEquals("1.2.0", invokeCleanVersionString("1.2.0-RELEASE"));
        assertEquals("1.2.0", invokeCleanVersionString("1.2.0-beta"));
        assertEquals("1.2.0", invokeCleanVersionString("v1.2.0-SNAPSHOT"));
        assertEquals("1.2.0", invokeCleanVersionString("1.2.0"));
    }

    @Test
    void testParseVersionSegment() throws Exception {
        assertEquals(1, invokeParseVersionSegment("1"));
        assertEquals(123, invokeParseVersionSegment("123"));
        assertEquals(1, invokeParseVersionSegment("1a"));
        assertEquals(1, invokeParseVersionSegment("1-beta"));
        assertEquals(0, invokeParseVersionSegment("abc"));
        assertEquals(0, invokeParseVersionSegment(""));
        assertEquals(0, invokeParseVersionSegment(null));
    }

    // Helper methods to invoke private methods via reflection
    private boolean invokeIsNewerVersion(String latest, String current) throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("isNewerVersion", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(updateChecker, latest, current);
    }

    private String invokeCleanVersionString(String version) throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("cleanVersionString", String.class);
        method.setAccessible(true);
        return (String) method.invoke(updateChecker, version);
    }

    private int invokeParseVersionSegment(String segment) throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("parseVersionSegment", String.class);
        method.setAccessible(true);
        return (int) method.invoke(updateChecker, segment);
    }
}
