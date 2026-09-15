package com.swe.ordersservice.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitExecutorTest {

    private AfterCommitExecutor afterCommitExecutor;

    @BeforeEach
    void setUp() {
        afterCommitExecutor = new AfterCommitExecutor();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("should execute action immediately when transaction synchronization is not active")
    void shouldExecuteActionImmediatelyWhenNoTransactionActive() {
        AtomicBoolean executed = new AtomicBoolean(false);

        afterCommitExecutor.execute(() -> executed.set(true));

        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("should register synchronization and execute action on afterCommit when transaction synchronization is active")
    void shouldExecuteActionOnAfterCommitWhenTransactionActive() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicBoolean executed = new AtomicBoolean(false);

        afterCommitExecutor.execute(() -> executed.set(true));

        // Before commit, action should not have run yet
        assertThat(executed.get()).isFalse();

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        // Simulate afterCommit callback
        synchronizations.getFirst().afterCommit();

        assertThat(executed.get()).isTrue();
    }
}
