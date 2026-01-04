package com.example.paxosgui;

import javafx.application.Application;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy jednostkowe GUI.
 *
 * Testy nie uruchamiają faktycznego interfejsu JavaFX,
 * lecz sprawdzają logikę pomocniczą oraz strukturę aplikacji.
 */
public class GuiTests {

    /**
     * Sprawdza, czy klasa HelloApplication
     * dziedziczy po javafx.application.Application.
     */
    @Test
    void testHelloApplicationIsJavaFxApp() {
        assertTrue(
                Application.class.isAssignableFrom(HelloApplication.class),
                "HelloApplication powinna dziedziczyć po Application"
        );
    }

    /**
     * Sprawdza, czy możliwe jest utworzenie instancji aplikacji GUI.
     */
    @Test
    void testHelloApplicationInstantiation() {
        HelloApplication app = new HelloApplication();
        assertNotNull(app);
    }

    /**
     * Sprawdza, czy metoda main istnieje.
     */
    @Test
    void testMainMethodExists() {
        assertDoesNotThrow(() -> {
            HelloApplication.class.getMethod("main", String[].class);
        });
    }

    /**
     * Testuje poprawne dodanie parametru do URL.
     */
    @Test
    void testAppendParamAddsParameter() {
        HelloApplication app = new HelloApplication();
        StringBuilder url = new StringBuilder("http://localhost/test");

        boolean first = invokeAppendParam(app, url, "value", "5", true);

        assertFalse(first);
        assertEquals("http://localhost/test?value=5", url.toString());
    }

    /**
     * Testuje odrzucenie niepoprawnej (ujemnej) wartości parametru.
     */
    @Test
    void testAppendParamRejectsInvalidValue() {
        HelloApplication app = new HelloApplication();
        StringBuilder url = new StringBuilder("http://localhost/test");

        boolean first = invokeAppendParam(app, url, "value", "-1", true);

        assertTrue(first);
        assertEquals("http://localhost/test", url.toString());
    }

    /**
     * Testuje filtr ograniczający pole tekstowe
     * do nieujemnych liczb całkowitych.
     */
    @Test
    void testOnlyPositiveNumbersFilter() {
        HelloApplication app = new HelloApplication();
        TextField field = new TextField();

        invokeOnlyPositiveNumbers(app, field);

        field.setText("12a3");
        assertEquals("123", field.getText());

        field.setText("-99");
        assertEquals("99", field.getText());
    }

    // =========================
    // METODY POMOCNICZE (reflection)
    // =========================

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

    private void invokeOnlyPositiveNumbers(HelloApplication app, TextField field) {
        try {
            var m = HelloApplication.class.getDeclaredMethod(
                    "onlyPositiveNumbers",
                    TextField.class
            );
            m.setAccessible(true);
            m.invoke(app, field);
        } catch (Exception e) {
            fail(e);
        }
    }
}
