/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.gateway.core.debug;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.core.Constants;
import org.wso2.carbon.gateway.core.config.ConfigRegistry;
import org.wso2.carbon.gateway.core.debug.utility.DebugCommandConstants;
import org.wso2.carbon.gateway.core.debug.utility.MediationFlowState;
import org.wso2.carbon.gateway.core.debug.utility.SharedChannelGroup;
import org.wso2.carbon.gateway.core.flow.AbstractMediator;
import org.wso2.carbon.gateway.core.flow.MediatorCollection;
import org.wso2.carbon.gateway.core.flow.Pipeline;
import org.wso2.carbon.gateway.core.inbound.InboundEndpoint;
import org.wso2.carbon.gateway.core.util.VariableUtil;
import org.wso2.carbon.messaging.CarbonMessage;

import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class GatewayDebugManager {
    private Logger log = LoggerFactory.getLogger(GatewayDebugManager.class);
    private MediationFlowState medFlowState = MediationFlowState.IDLE;
    public static volatile Semaphore mediationFlowSem;
    private static volatile ReentrantLock mediationFlowLock;
    private CarbonMessage carbonMessage;
    private ConfigRegistry configRegistry;

    public GatewayDebugManager() {
        mediationFlowSem = new Semaphore(0);
        mediationFlowLock = new ReentrantLock();
        configRegistry = ConfigRegistry.getInstance();
    }

    public void processDebugCommand(String command) {
        try {
            JSONObject parsedDebugCommand = new JSONObject(command);
            String debugCommand = "";
            if (parsedDebugCommand.has(DebugCommandConstants.DEBUG_COMMAND)) {
                debugCommand = parsedDebugCommand.getString(DebugCommandConstants.DEBUG_COMMAND);
            } else {
                return;
            }

            if (debugCommand.equals(DebugCommandConstants.DEBUG_COMMAND_SET)) {
                String debugCommandArgument = parsedDebugCommand.getString(DebugCommandConstants
                                                                                   .DEBUG_COMMAND_ARGUMENT);
                if (debugCommandArgument.equals(DebugCommandConstants.DEBUG_COMMAND_PARAMETER)) {
                    JSONObject parameterArguments = parsedDebugCommand
                            .getJSONObject(DebugCommandConstants.DEBUG_COMMAND_PARAMETER);
                    this.changeVariableDetails(parameterArguments, true);
                } else {
                    String inboundName = parsedDebugCommand
                            .getString(DebugCommandConstants.DEBUG_COMMAND_INBOUND);
                    JSONObject pipeLine = parsedDebugCommand
                            .getJSONObject(DebugCommandConstants.DEBUG_COMMAND_PIPELINE);

                    String pipeLineName = pipeLine.getString(DebugCommandConstants.DEBUG_COMMAND_PIPELINE_NAME);
                    String mediatorPositions = pipeLine.getString(DebugCommandConstants.DEBUG_COMMAND_PIPELINE_MEDIATOR_POSITIONS);

                    if (debugCommandArgument.equals(DebugCommandConstants.DEBUG_COMMAND_BREAKPOINT)) {
                        registerBreakPointsAndSkipPoints(inboundName, pipeLineName, mediatorPositions,
                                                         true, true);
                    } else if (debugCommandArgument.equals(DebugCommandConstants.DEBUG_COMMAND_SKIP)) {
                        registerBreakPointsAndSkipPoints(inboundName, pipeLineName, mediatorPositions,
                                                         false, true);
                    }
                }
            } else if (debugCommand.equals(DebugCommandConstants.DEBUG_COMMAND_CLEAR)) {
                String debugCommandArgument = parsedDebugCommand.getString(DebugCommandConstants
                                                                                   .DEBUG_COMMAND_ARGUMENT);
                if (debugCommandArgument.equals(DebugCommandConstants.DEBUG_COMMAND_PARAMETER)) {
                    JSONObject parameterArguments = parsedDebugCommand
                            .getJSONObject(DebugCommandConstants.DEBUG_COMMAND_PARAMETER);
                    this.changeVariableDetails(parameterArguments, false);
                } else {
                    String inboundName = parsedDebugCommand
                            .getString(DebugCommandConstants.DEBUG_COMMAND_INBOUND);
                    JSONObject pipeLine = parsedDebugCommand
                            .getJSONObject(DebugCommandConstants.DEBUG_COMMAND_PIPELINE);

                    String pipeLineName = pipeLine.getString(DebugCommandConstants.DEBUG_COMMAND_PIPELINE_NAME);
                    String mediatorPositions = pipeLine.getString(DebugCommandConstants.DEBUG_COMMAND_PIPELINE_MEDIATOR_POSITIONS);

                    if (debugCommandArgument.equals(DebugCommandConstants.DEBUG_COMMAND_BREAKPOINT)) {
                        registerBreakPointsAndSkipPoints(inboundName, pipeLineName, mediatorPositions,
                                                         true, false);
                    } else if (debugCommandArgument.equals(DebugCommandConstants.DEBUG_COMMAND_SKIP)) {
                        registerBreakPointsAndSkipPoints(inboundName, pipeLineName, mediatorPositions,
                                                         false, false);
                    }
                }
            } else if (debugCommand.equals(DebugCommandConstants.DEBUG_COMMAND_GET)) {
                String commandArguments = parsedDebugCommand.getString(DebugCommandConstants.DEBUG_COMMAND_ARGUMENT);
                JSONObject parameterArgument = null;
                if (commandArguments.equals(DebugCommandConstants.DEBUG_COMMAND_PARAMETER)) {
                    parameterArgument = parsedDebugCommand
                            .getJSONObject(DebugCommandConstants.DEBUG_COMMAND_PARAMETER);
                }
                this.getParameterDetails(commandArguments, parameterArgument);
            } else if (debugCommand.equals(DebugCommandConstants.DEBUG_COMMAND_RESUME)) {
                debugResume();
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Debug command not found");
                }
                this.publishCommandResponse(createDebugCommandResponse(false,
                                                                       DebugCommandConstants.DEBUG_COMMAND_RESPONSE_COMMAND_NOT_FOUND).toString());
            }
        } catch (JSONException ex) {
            log.error("Unable to process debug command", ex);
        }
    }


    private void debugResume() {
        this.transitMediationFlowStateToActive();
        this.publishCommandResponse(createDebugCommandResponse(true, null).toString());
    }

    /**
     * Transit the mediation flow state to the SUSPENDED from previous UNKNOWN state
     * Transiting to SUSPENDED state will put the calling thread to sleep as sem.down() is called
     */
    private void transitMediationFlowStateToSuspended() {
        if (this.medFlowState == MediationFlowState.IDLE
            || this.medFlowState == MediationFlowState.ACTIVE) {
            medFlowState = MediationFlowState.SUSPENDED;
            try {
                mediationFlowSem.acquire();
            } catch (InterruptedException ex) {
                log.error("Unable to suspend the mediation flow thread", ex);
            }
        }
    }

    /**
     * Transit the mediation flow state to the ACTIVE from previous UNKNOWN state
     * Transiting to ACTIVE state will put the calling thread awakes as sem.up() is called
     */
    public void transitMediationFlowStateToActive() {
        if (this.medFlowState == MediationFlowState.SUSPENDED) {
            medFlowState = MediationFlowState.ACTIVE;
            mediationFlowSem.release();
        }
    }


    private void changeVariableDetails(JSONObject variableArguments, boolean isSet) {
        try {
            String variableName = variableArguments
                    .getString(DebugCommandConstants.DEBUG_COMMAND_PARAMETER_NAME);
            String variableValue = null;
            if(variableArguments.has(DebugCommandConstants.DEBUG_COMMAND_PARAMETER_VALUE)) {
                variableValue = variableArguments
                        .getString(DebugCommandConstants.DEBUG_COMMAND_PARAMETER_VALUE);
            }

            if (isSet) {
                VariableUtil.addVariable(carbonMessage, variableName, variableValue);
            } else {
                VariableUtil.removeVariable(carbonMessage, variableName);
            }
            this.publishCommandResponse(createDebugCommandResponse(true, null).toString());
        } catch (JSONException e) {
            log.error("Failed to set or remove variable " + variableArguments, e);
        }
    }

    private void registerBreakPointsAndSkipPoints(String inboundName, String pipeLineName, String
            positions, boolean isBreakPoint, boolean isSet) {

        String[] mediatorPositions = positions.split("\\s+");
        InboundEndpoint inboundEndpoint = configRegistry.getInboundEndpoint(inboundName);

        if (inboundEndpoint == null) {
            this.publishCommandResponse(createDebugCommandResponse(false,
                                                                   DebugCommandConstants.DEBUG_COMMAND_RESPONSE_UNABLE_TO_FIND_INBOUND_ENDPOINT).toString());
            return;
        }

        Pipeline pipeline = configRegistry.getPipeline(inboundEndpoint.getPipeline());

        if (pipeline == null) {
            this.publishCommandResponse(createDebugCommandResponse(false,
                                                                   DebugCommandConstants.DEBUG_COMMAND_RESPONSE_UNABLE_TO_FIND_PIPELINE).toString());
            return;
        }
        MediatorCollection mediators = pipeline.getMediators();

        for (int i = 0; i < mediatorPositions.length; i++) {
            try {
                if (isBreakPoint) {
                    if (isSet) {
                        ((AbstractMediator) mediators.getMediators().get(Integer.parseInt(mediatorPositions[i])))
                                .setBreakPoint
                                        (true);
                        ((AbstractMediator) mediators.getMediators().get(Integer.parseInt(mediatorPositions[i])))
                                .setPipelineName(pipeLineName);
                        ((AbstractMediator) mediators.getMediators().get(Integer.parseInt(mediatorPositions[i])))
                                .setMediatorPosition(Integer.parseInt(mediatorPositions[i]));
                    } else {
                        ((AbstractMediator) mediators.getMediators().get(Integer.parseInt(mediatorPositions[i]))).setBreakPoint(false);
                        ((AbstractMediator) mediators.getMediators().get(Integer.parseInt(mediatorPositions[i])))
                                .setPipelineName(null);
                        ((AbstractMediator) mediators.getMediators().get(Integer.parseInt(mediatorPositions[i])))
                                .setMediatorPosition(-1);
                    }
                } else {
                    if (isSet) {
                        ((AbstractMediator) mediators.getMediators().get(Integer.parseInt(mediatorPositions[i]))).setSkipPoint(true);
                    } else {
                        ((AbstractMediator) mediators.getMediators().get(Integer.parseInt(mediatorPositions[i]))).setSkipPoint(false);
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                log.error("Invalid Mediator Position[" + mediatorPositions[i] + "]");
            }
        }
        this.publishCommandResponse(createDebugCommandResponse(true, null).toString());
    }


    private void getParameterDetails(String commandArgument, JSONObject parameter) {
        if (!(this.medFlowState == MediationFlowState.SUSPENDED)) {
            this.publishCommandResponse(createDebugCommandResponse(false,
                                                                   DebugCommandConstants.DEBUG_COMMAND_RESPONSE_UNABLE_TO_ACQUIRE_MESSAGE_PARAMETERS).toString());
            return;
        }

        try {
            if (commandArgument.equals(DebugCommandConstants.DEBUG_PARAMETERS)) {
                JSONObject result = new JSONObject();
                result.put(DebugCommandConstants.DEBUG_PARAMETERS, getVariables());

                JSONObject headers = new JSONObject();
                for (Map.Entry entry : carbonMessage.getHeaders().entrySet()) {
                    headers.put(entry.getKey().toString(), entry.getValue());
                }
                result.put(DebugCommandConstants.DEBUG_HEADERS, headers);
                if (carbonMessage.getMessageDataSource() != null) {
                    result.put(DebugCommandConstants.DEBUG_BODY, carbonMessage.getMessageDataSource().getDataObject()
                            .toString());
                }
                SharedChannelGroup.getInstance().writeAndFlush(result);
            } else if (commandArgument.equals(DebugCommandConstants.DEBUG_PARAMETER)) {
                if (parameter != null) {
                    JSONObject result = new JSONObject();
                    String parameterName = parameter.getString(DebugCommandConstants.DEBUG_PARAMETER_NAME);
                    if(VariableUtil.getVariable(carbonMessage, parameterName) != null) {
                        result.put(parameterName, VariableUtil.getVariable(carbonMessage, parameterName).toString());
                    }
                    SharedChannelGroup.getInstance().writeAndFlush(result);
                }
            }
        } catch (JSONException e) {
            log.error("Exception occurred while retrieving the parameters", e);
        }
    }

    public JSONObject createDebugCommandResponse(boolean isPositive, String failedReason) {
        JSONObject response;
        response = new JSONObject();
        try {
            if (isPositive) {
                response.put(DebugCommandConstants.DEBUG_COMMAND_RESPONSE,
                             DebugCommandConstants.DEBUG_COMMAND_RESPONSE_SUCCESSFUL);
            } else {
                response.put(DebugCommandConstants.DEBUG_COMMAND_RESPONSE,
                             DebugCommandConstants.DEBUG_COMMAND_RESPONSE_FAILED);
                response.put(DebugCommandConstants.DEBUG_COMMAND_RESPONSE_FAILED_REASON, failedReason);
            }
        } catch (JSONException e) {
            log.error("Unable to advertise command response", e);
        }
        return response;
    }

    public void publishCommandResponse(String commandResponse) {
        SharedChannelGroup.getInstance().writeAndFlush(commandResponse);
    }

    /**
     * advertise a mediation skip to the communication channel
     */
    public void advertiseMediationFlowSkip(CarbonMessage carbonMessage, String pipeLineName, int mediatorPosition) {
        setCarbonMessage(carbonMessage);
        this.publishCommandResponse(this.createDebugMediationFlowPointHitEvent(false, pipeLineName, mediatorPosition).toString());
        if (log.isDebugEnabled()) {
            log.debug("Mediation Flow skipped");
        }
    }

    public JSONObject createDebugMediationFlowPointHitEvent(boolean isBreakpoint, String pipeLineName, int
            mediatorPosition) {
        JSONObject event = null;
        try {
            event = new JSONObject();
            if (isBreakpoint) {
                event.put(DebugEventConstants.DEBUG_EVENT, DebugEventConstants.DEBUG_EVENT_BREAKPOINT);
            } else {
                event.put(DebugEventConstants.DEBUG_EVENT, DebugEventConstants.DEBUG_EVENT_SKIP);
            }
            JSONObject position = new JSONObject();
            position.put(DebugCommandConstants.DEBUG_COMMAND_PIPELINE, pipeLineName);
            position.put(DebugCommandConstants.DEBUG_COMMAND_PIPELINE_MEDIATOR_POSITIONS, mediatorPosition);

            event.put(DebugCommandConstants.DEBUG_MEDIATOR_POSITION, position);
            event.put(DebugCommandConstants.DEBUG_PARAMETERS, getVariables());
            JSONObject headers = new JSONObject();
            for (Map.Entry entry : carbonMessage.getHeaders().entrySet()) {
                headers.put(entry.getKey().toString(), entry.getValue());
            }
            event.put(DebugCommandConstants.DEBUG_HEADERS, headers);
            if (carbonMessage.getMessageDataSource() != null) {
                event.put(DebugCommandConstants.DEBUG_BODY, carbonMessage.getMessageDataSource().getDataObject()
                        .toString());
            }
        } catch (JSONException ex) {
            log.error("Failed to create debug event in JSON format", ex);
        }
        return event;
    }

    /**
     * advertise a mediation breakpoint to the communication channel
     */
    public void publishMediationFlowBreakPoint(CarbonMessage carbonMessage, String pipeLineName, int mediatorPosition) {
        setCarbonMessage(carbonMessage);
        this.publishCommandResponse(this.createDebugMediationFlowPointHitEvent(true, pipeLineName, mediatorPosition).toString());
        if (log.isDebugEnabled()) {
            log.debug("Mediation flow suspended");
        }
        this.transitMediationFlowStateToSuspended();
        this.publishCommandResponse(this.createDebugEvent(DebugEventConstants.DEBUG_EVENT_RESUMED_CLIENT).toString());
        if (log.isDebugEnabled()) {
            log.info("Mediation flow resumed from suspension");
        }
    }

    public JSONObject createDebugEvent(String eventString) {
        JSONObject event = null;
        try {
            event = new JSONObject();
            event.put(DebugEventConstants.DEBUG_EVENT, eventString);
        } catch (JSONException ex) {
            log.error("Failed to create debug event in JSON format", ex);
        }
        return event;
    }

    /**
     * Acquiring hold on this lock make sure that only one mediation flow
     * is due inside mediation engine
     */
    public void acquireMediationFlowLock() {
        mediationFlowLock.lock();
    }

    /**
     * Releasing hold on this lock make sure that next mediation flow is started after
     * completion of the previous
     */
    public void releaseMediationFlowLock() {
        mediationFlowLock.unlock();
    }

    /**
     * Related to publishing the point where mediation flow starts.
     *
     * @param message Carbon message
     */
    public void publishMediationFlowStartPoint(CarbonMessage message) {
        if (message.isDebugEnabled()) {
            setCarbonMessage(message);
            this.publishCommandResponse(this.createDebugEvent
                    (DebugEventConstants.DEBUG_EVENT_STARTED).toString());
            if (log.isDebugEnabled()) {
                log.debug("Mediation flow started");
            }
        }
    }

    /**
     * related to publish mediation flow terminating point to the communication channel
     *
     * @param message Carbon message
     */
    public void publishMediationFlowTerminatePoint(CarbonMessage message) {
        if (message.isDebugEnabled()) {
            this.publishCommandResponse(this.createDebugEvent(DebugEventConstants.DEBUG_EVENT_TERMINATED).toString());
            if (log.isDebugEnabled()) {
                log.debug("Mediation flow terminated");
            }
        }
    }

    private JSONObject getVariables() throws JSONException {
        JSONObject variables = new JSONObject();
        Object variableStack = carbonMessage.getProperty(Constants.VARIABLE_STACK);
        if (variableStack != null) {
            Stack<Map<String, Object>> varStack = (Stack<Map<String, Object>>) variableStack;
            Map<String, Object> peekElement = varStack.peek();

            for(Map.Entry entry : peekElement.entrySet()) {
                if(entry.getKey().toString().equals(Constants.GW_GT_SCOPE)) {
                    continue;
                }
                variables.put(entry.getKey().toString(), entry.getValue());
            }
            if (peekElement != null) {
                Map<String, Object> gtScope = (Map<String, Object>) peekElement.get(Constants.GW_GT_SCOPE);
                if(gtScope != null) {
                    for (Map.Entry entry : gtScope.entrySet()) {
                        variables.put(entry.getKey().toString(), entry.getValue());
                    }
                }
            }
        }
        return variables;
    }

    public void setCarbonMessage(CarbonMessage carbonMessage) {
        this.carbonMessage = carbonMessage;
    }
}
