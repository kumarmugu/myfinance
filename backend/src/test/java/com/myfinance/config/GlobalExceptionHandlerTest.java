package com.myfinance.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleReferenceConstraintException() {
        ReferenceConstraintException ex =
                new ReferenceConstraintException("Owner", List.of("Account: Tiger", "Account: IBKR"));

        ResponseEntity<Map<String, Object>> response = handler.handleReferenceConstraintException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("Owner"));
        @SuppressWarnings("unchecked")
        List<String> refs = (List<String>) response.getBody().get("references");
        assertEquals(2, refs.size());
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void shouldHandleRuntimeException() {
        RuntimeException ex = new RuntimeException("Something broke");

        ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Something broke", response.getBody().get("message"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().get("status"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void shouldHandleValidationException() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "must not be blank"));
        bindingResult.addError(new FieldError("target", "amount", "must be positive"));

        // A dummy MethodParameter is required by the constructor.
        MethodParameter methodParameter =
                new MethodParameter(this.getClass().getDeclaredMethod("shouldHandleValidationException"), -1);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().get("status"));
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("errors");
        assertEquals(2, fieldErrors.size());
        assertEquals("must not be blank", fieldErrors.get("name"));
        assertEquals("must be positive", fieldErrors.get("amount"));
    }
}
