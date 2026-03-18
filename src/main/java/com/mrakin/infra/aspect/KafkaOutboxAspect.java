package com.mrakin.infra.aspect;

import com.mrakin.domain.event.UrlAccessedEvent;
import com.mrakin.domain.model.Url;
import com.mrakin.usecases.UrlAccessedKafkaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaOutboxAspect {

    private final ApplicationEventPublisher eventPublisher;
    private final ExpressionParser parser = new SpelExpressionParser();

    @AfterReturning(pointcut = "@annotation(urlAccessed)", returning = "result")
    public void publishEvent(JoinPoint joinPoint, UrlAccessedKafkaEvent urlAccessed, Object result) {
        if (result instanceof Url url) {
            log.debug("Intercepted URL access for short code: {}", url.getShortCode());
            
            // Если в аннотации указан ключ через SpEL, мы могли бы его использовать, 
            // но в нашей задаче партиционирование идет по домену из Url POJO.
            // Тем не менее, пробросим ключ, если он нужен для других целей.
            String key = evaluateKey(joinPoint, urlAccessed.key());
            
            // Публикуем событие в контекст Spring.
            // Spring Modulith подхватит его, сохранит в таблицу и отправит в Kafka.
            eventPublisher.publishEvent(new UrlAccessedEvent(url));
        }
    }

    private String evaluateKey(JoinPoint joinPoint, String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();
            String[] parameterNames = signature.getParameterNames();

            StandardEvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
            return parser.parseExpression(expression).getValue(context, String.class);
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL expression: {}", expression, e);
            return null;
        }
    }
}
