package com.bookstore.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Cross-cutting logging for the service layer: logs method entry with arguments
 * and the elapsed time on completion (or failure), without touching business code.
 *
 * <p>Note for Phase 3: once auth/user data flows through the service layer, this
 * aspect must not log sensitive arguments (passwords, tokens) — see BACKLOG.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("execution(* com.bookstore.service..*.*(..))")
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
