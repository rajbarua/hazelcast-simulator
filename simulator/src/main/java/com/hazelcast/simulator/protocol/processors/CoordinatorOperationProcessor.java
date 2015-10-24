/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.simulator.protocol.processors;

import com.hazelcast.simulator.coordinator.FailureContainer;
import com.hazelcast.simulator.coordinator.PerformanceStateContainer;
import com.hazelcast.simulator.coordinator.TestHistogramContainer;
import com.hazelcast.simulator.coordinator.TestPhaseListenerContainer;
import com.hazelcast.simulator.protocol.core.ResponseType;
import com.hazelcast.simulator.protocol.core.SimulatorAddress;
import com.hazelcast.simulator.protocol.exception.LocalExceptionLogger;
import com.hazelcast.simulator.protocol.operation.ExceptionOperation;
import com.hazelcast.simulator.protocol.operation.FailureOperation;
import com.hazelcast.simulator.protocol.operation.OperationType;
import com.hazelcast.simulator.protocol.operation.PerformanceStateOperation;
import com.hazelcast.simulator.protocol.operation.PhaseCompletedOperation;
import com.hazelcast.simulator.protocol.operation.SimulatorOperation;
import com.hazelcast.simulator.protocol.operation.TestHistogramOperation;

import static com.hazelcast.simulator.protocol.core.ResponseType.SUCCESS;
import static com.hazelcast.simulator.protocol.core.ResponseType.UNSUPPORTED_OPERATION_ON_THIS_PROCESSOR;

/**
 * An {@link OperationProcessor} implementation to process {@link SimulatorOperation} instances on a Simulator Coordinator.
 */
public class CoordinatorOperationProcessor extends OperationProcessor {

    private final LocalExceptionLogger exceptionLogger;
    private final TestPhaseListenerContainer testPhaseListenerContainer;
    private final PerformanceStateContainer performanceStateContainer;
    private final TestHistogramContainer testHistogramContainer;
    private final FailureContainer failureContainer;

    public CoordinatorOperationProcessor(LocalExceptionLogger exceptionLogger,
                                         TestPhaseListenerContainer testPhaseListenerContainer,
                                         PerformanceStateContainer performanceStateContainer,
                                         TestHistogramContainer testHistogramContainer, FailureContainer failureContainer) {
        super(exceptionLogger);
        this.exceptionLogger = exceptionLogger;
        this.testPhaseListenerContainer = testPhaseListenerContainer;
        this.performanceStateContainer = performanceStateContainer;
        this.testHistogramContainer = testHistogramContainer;
        this.failureContainer = failureContainer;
    }

    @Override
    protected ResponseType processOperation(OperationType operationType, SimulatorOperation operation,
                                            SimulatorAddress sourceAddress) throws Exception {
        switch (operationType) {
            case EXCEPTION:
                processException((ExceptionOperation) operation);
                break;
            case PHASE_COMPLETED:
                processPhaseCompletion((PhaseCompletedOperation) operation);
                break;
            case PERFORMANCE_STATE:
                processPerformanceState((PerformanceStateOperation) operation, sourceAddress);
                break;
            case TEST_HISTOGRAMS:
                processTestHistogram((TestHistogramOperation) operation, sourceAddress);
                break;
            case FAILURE:
                processFailure((FailureOperation) operation);
                break;
            default:
                return UNSUPPORTED_OPERATION_ON_THIS_PROCESSOR;
        }
        return SUCCESS;
    }

    private void processException(ExceptionOperation operation) {
        exceptionLogger.logOperation(operation);
    }

    private void processPhaseCompletion(PhaseCompletedOperation operation) {
        testPhaseListenerContainer.updatePhaseCompletion(operation.getTestId(), operation.getTestPhase());
    }

    private void processPerformanceState(PerformanceStateOperation operation, SimulatorAddress sourceAddress) {
        performanceStateContainer.updatePerformanceState(sourceAddress, operation.getPerformanceStates());
    }

    private void processTestHistogram(TestHistogramOperation operation, SimulatorAddress sourceAddress) {
        testHistogramContainer.addTestHistograms(sourceAddress, operation.getTestId(), operation.getProbeHistograms());
    }

    private void processFailure(FailureOperation operation) {
        failureContainer.addFailureOperation(operation);
    }
}
