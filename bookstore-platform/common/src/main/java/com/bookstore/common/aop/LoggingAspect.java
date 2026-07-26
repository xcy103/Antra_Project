package com.bookstore.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Cross-cutting logging for the service layer of every service: logs method entry
 * with arguments and elapsed time. The pointcut matches any {@code service}
 * sub-package under {@code com.bookstore} (e.g. {@code com.bookstore.bookservice.service}).
 *
 * <p>Credential DTOs redact their password in {@code toString()}, so this never
 * logs plaintext secrets.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("execution(* com.bookstore..service..*.*(..))")
    public void serviceLayer() {
    }

    @Around("serviceLayer()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();
        log.info("→ {} args={}", method, Arrays.toString(joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            log.info("← {} completed in {} ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            log.warn("✗ {} failed in {} ms: {}", method, System.currentTimeMillis() - start, ex.toString());
            throw ex;
        }
    }
}
