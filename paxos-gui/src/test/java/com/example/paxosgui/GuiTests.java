package com.example.paxosgui;

import javafx.application.Application;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GuiTests {


    @Test
    void testHelloApplicationIsJavaFxApp() {
        assertTrue(
                Application.class.isAssignableFrom(HelloApplication.class),
                "HelloApplication powinna dziedziczyć po Application"
        );
    }


    @Test
    void testHelloApplicationInstantiation() {
        HelloApplication app = new HelloApplication();
        assertNotNull(app);
    }


    @Test
    void testMainMethodExists() {
        assertDoesNotThrow(() -> {
            HelloApplication.class.getMethod("main", String[].class);
        });
    }


    @Test
    void testAppendParamAddsParameter() {
        HelloApplication app = new HelloApplication();
        StringBuilder url = new StringBuilder("http://localhost/test");

        boolean first = invokeAppendParam(app, url, "value", "5", true);

        assertFalse(first);
        assertEquals("http://localhost/test?value=5", url.toString());
    }


    @Test
    void testAppendParamRejectsInvalidValue() {
        HelloApplication app = new HelloApplication();
        StringBuilder url = new StringBuilder("http://localhost/test");

        boolean first = invokeAppendParam(app, url, "value", "-1", true);

        assertTrue(first);
        assertEquals("http://localhost/test", url.toString());
    }

    private boolean invokeAppendParam(
            HelloApplication app,
            StringBuilder url,
            String name,
            String value,
            boolean first
    ) {
        try {
            var m = HelloApplication.class.getDeclaredMethod(
                    "appendParam",
                    StringBuilder.class,
                    String.class,
                    String.class,
                    boolean.class
            );
            m.setAccessible(true);
            return (boolean) m.invoke(app, url, name, value, first);
        } catch (Exception e) {
            fail(e);
            return true;
        }
    }



}
