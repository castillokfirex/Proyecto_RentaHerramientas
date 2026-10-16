package com.rentatools.shared.infrastructure.config;

import java.util.List;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Configura transacciones declarativas por AOP sobre todos los servicios de aplicación
 * ({@code com.rentatools..application.service..*}), manteniendo la capa de aplicación libre de {@code @Transactional}.
 */
@Configuration
public class UseCaseTransactionConfig {

    @Bean
    public Advisor useCaseTransactionAdvisor(PlatformTransactionManager transactionManager) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(public * com.rentatools..application.service..*(..))");

        RuleBasedTransactionAttribute readOnlyAttr = new RuleBasedTransactionAttribute();
        readOnlyAttr.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);
        readOnlyAttr.setReadOnly(true);

        RuleBasedTransactionAttribute writeAttr = new RuleBasedTransactionAttribute();
        writeAttr.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        writeAttr.setRollbackRules(List.of(new RollbackRuleAttribute(Exception.class)));

        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("get*", readOnlyAttr);
        source.addTransactionalMethod("find*", readOnlyAttr);
        source.addTransactionalMethod("list*", readOnlyAttr);
        source.addTransactionalMethod("search*", readOnlyAttr);
        source.addTransactionalMethod("quote*", readOnlyAttr);
        source.addTransactionalMethod("*", writeAttr);

        TransactionInterceptor interceptor = new TransactionInterceptor(transactionManager, source);
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }
}
