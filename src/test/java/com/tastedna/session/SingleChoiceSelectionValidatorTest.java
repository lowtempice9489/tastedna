package com.tastedna.session;

import com.tastedna.session.application.SingleChoiceSelectionValidator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SingleChoiceSelectionValidatorTest {

    private static final Set<String> ALLOWED_OPTIONS =
            Set.of("A", "B", "C", "D", "E", "F");

    @Test
    void acceptsAllowedOption() {
        assertDoesNotThrow(() ->
                SingleChoiceSelectionValidator.validate(
                        "A",
                        ALLOWED_OPTIONS
                )
        );
    }

    @Test
    void rejectsUnknownOption() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceSelectionValidator.validate(
                        "G",
                        ALLOWED_OPTIONS
                )
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNullSelection() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceSelectionValidator.validate(
                        null,
                        ALLOWED_OPTIONS
                )
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsEmptyOptionDefinition() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceSelectionValidator.validate(
                        "A",
                        Set.of()
                )
        );

        assertEquals(
                "INVALID_OPTION_DEFINITION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNullOptionDefinition() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceSelectionValidator.validate(
                        "A",
                        null
                )
        );

        assertEquals(
                "INVALID_OPTION_DEFINITION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsBlankOptionSelection() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceSelectionValidator.validate(
                        "   ",
                        Set.of("A", "B", "C")
                )
        );

        assertEquals(
                "INVALID_OPTION_SELECTION",
                exception.getMessage()
        );
    }

    @Test
    void rejectsBlankOptionInDefinition() {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SingleChoiceSelectionValidator.validate(
                        "A",
                        Set.of("A", "B", "   ")
                )
        );

        assertEquals(
                "INVALID_OPTION_DEFINITION",
                exception.getMessage()
        );
    }

}
