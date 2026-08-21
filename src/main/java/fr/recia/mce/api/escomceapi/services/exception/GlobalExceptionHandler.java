/*
 * Copyright (C) 2023 GIP-RECIA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.recia.mce.api.escomceapi.services.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PersonneNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePersonneNotFound(PersonneNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ErrorResponse> handleWeakPassword(WeakPasswordException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("WEAK_PASSWORD", ex.getMessage()));
    }

    @ExceptionHandler(InvalidAvatarException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAvatar(InvalidAvatarException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_AVATAR", ex.getMessage()));
    }

    @ExceptionHandler(ChampsObligatoiresException.class)
    public ResponseEntity<ErrorResponse> handleChampsObligatoires(ChampsObligatoiresException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCode(InvalidCodeException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_CODE", ex.getMessage()));
    }

    @ExceptionHandler(CodeExpiredException.class)
    public ResponseEntity<ErrorResponse> handleCodeExpired(CodeExpiredException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("CODE_EXPIRED", ex.getMessage()));
    }

    @ExceptionHandler(MaxAttemptsExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxAttempts(MaxAttemptsExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("MAX_ATTEMPTS_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ErrorResponse> handleInactiveAccount(InactiveAccountException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INACTIVE_ACCOUNT", ex.getMessage()));
    }

    @ExceptionHandler(CharteNotAcceptedException.class)
    public ResponseEntity<ErrorResponse> handleCharteNotAccepted(CharteNotAcceptedException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("CHARTE_REQUIRED", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.toList());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Erreurs de validation", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "JSON invalide"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "Ressource non trouvée : " + ex.getRequestURL()));
    }

    // Toujours en dernier
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Une erreur inattendue s'est produite lors du traitement de la requête [{}] - Détail : {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "Une erreur interne est survenue"));
    }
}
