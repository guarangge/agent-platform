package com.zc.agent.core.modules.runtime.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionStatusFlowServiceTransactionTest {

    @Test
    void shouldPersistEveryStatusTransitionInIndependentTransaction() {
        for (Method method : ExecutionStatusFlowService.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("mark")) continue;
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional).as(method.getName()).isNotNull();
            assertThat(transactional.propagation()).as(method.getName()).isEqualTo(Propagation.REQUIRES_NEW);
        }
    }
}
