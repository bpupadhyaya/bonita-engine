/**
 * Copyright (C) 2011-2013 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation
 * version 2.1 of the License.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
 * Floor, Boston, MA 02110-1301, USA.
 **/
package org.bonitasoft.engine.bpm.process.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.bonitasoft.engine.bpm.actor.ActorDefinition;
import org.bonitasoft.engine.bpm.connector.ConnectorEvent;
import org.bonitasoft.engine.bpm.document.DocumentDefinition;
import org.bonitasoft.engine.bpm.flownode.ActivityDefinition;
import org.bonitasoft.engine.bpm.flownode.AutomaticTaskDefinition;
import org.bonitasoft.engine.bpm.flownode.BoundaryEventDefinition;
import org.bonitasoft.engine.bpm.flownode.CallActivityDefinition;
import org.bonitasoft.engine.bpm.flownode.CatchMessageEventTriggerDefinition;
import org.bonitasoft.engine.bpm.flownode.EndEventDefinition;
import org.bonitasoft.engine.bpm.flownode.FlowNodeDefinition;
import org.bonitasoft.engine.bpm.flownode.GatewayDefinition;
import org.bonitasoft.engine.bpm.flownode.GatewayType;
import org.bonitasoft.engine.bpm.flownode.IntermediateCatchEventDefinition;
import org.bonitasoft.engine.bpm.flownode.ReceiveTaskDefinition;
import org.bonitasoft.engine.bpm.flownode.SendTaskDefinition;
import org.bonitasoft.engine.bpm.flownode.StartEventDefinition;
import org.bonitasoft.engine.bpm.flownode.TimerEventTriggerDefinition;
import org.bonitasoft.engine.bpm.flownode.TimerType;
import org.bonitasoft.engine.bpm.flownode.TransitionDefinition;
import org.bonitasoft.engine.bpm.flownode.impl.FlowElementContainerDefinition;
import org.bonitasoft.engine.bpm.flownode.impl.FlowElementContainerDefinitionImpl;
import org.bonitasoft.engine.bpm.flownode.impl.MultiInstanceLoopCharacteristics;
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition;
import org.bonitasoft.engine.bpm.process.InvalidProcessDefinitionException;
import org.bonitasoft.engine.bpm.process.SubProcessDefinition;
import org.bonitasoft.engine.expression.Expression;

/**
 * Build a process definition
 * 
 * @author Baptiste Mesta
 * @author Yanyan Liu
 * @author Elias Ricken de Medeiros
 * @author Matthieu Chaffotte
 */
public class ProcessDefinitionBuilder implements DescriptionBuilder, ContainerBuilder {

    private static final String DOUBLE_HYPHEN = "--";

    private static final int MAX_CHARACTER_URL = 255;

    private static final int MAX_CHARACTER_FILENAME = 255;

    protected DesignProcessDefinitionImpl process;

    private List<String> designErrors;

    public ProcessDefinitionBuilder createNewInstance(final String name, final String version) {
        designErrors = new ArrayList<String>(5);
        if (name == null || name.isEmpty()) {
            designErrors.add("name of the process is null or empty");
        }
        process = new DesignProcessDefinitionImpl(name, version);
        process.setProcessContainer(new FlowElementContainerDefinitionImpl());
        return this;
    }

    public DesignProcessDefinition done() throws InvalidProcessDefinitionException {
        validateProcess();
        if (designErrors.isEmpty()) {
            return process;
        } else {
            throw new InvalidProcessDefinitionException(designErrors);
        }
    }

    private void validateProcess() {
        validateProcessAttributes();
        validateUniqueNames(); // FIXME: can be removed after ids are added in flow nodes
        final FlowElementContainerDefinition processContainer = process.getProcessContainer();
        validateProcess(processContainer, true);
        validateEventsSubProcess();
        validateActors();
    }

    private void validateActors() {
        final ActorDefinition actorInitiator = process.getActorInitiator();
        if (actorInitiator != null) {
            final ActorDefinition actor = process.getActor(actorInitiator.getName());
            if (actor == null) {
                designErrors.add("No actor is found for initiator '" + actorInitiator.getName() + "'.");
            }
        }
    }

    private void validateProcess(final FlowElementContainerDefinition flowElementContainer, final boolean isRootContainer) {
        validateGateways(flowElementContainer);
        validateDocuments(flowElementContainer);
        validateMultiInstances(flowElementContainer);
        validateEvents(flowElementContainer, isRootContainer);
        validateActivities(flowElementContainer);

    }

    private void validateUniqueNames() {
        final FlowElementContainerDefinition processContainer = process.getProcessContainer();
        final List<String> names = new ArrayList<String>();
        validateUniqueNames(processContainer, names);

    }

    private void validateUniqueNames(final FlowElementContainerDefinition processContainer, final List<String> names) {
        validateFlowNodeName(names, processContainer.getActivities());
        validateFlowNodeName(names, processContainer.getEndEvents());
        validateFlowNodeName(names, processContainer.getGateways());
        validateFlowNodeName(names, processContainer.getIntermediateCatchEvents());
        validateFlowNodeName(names, processContainer.getIntermediateThrowEvents());
        validateFlowNodeName(names, processContainer.getStartEvents());
    }

    private void validateFlowNodeName(final List<String> names, final Collection<? extends FlowNodeDefinition> flowNodes) {
        for (final FlowNodeDefinition flowNode : flowNodes) {
            if (names.contains(flowNode.getName())) {
                designErrors.add("More than one elements are named '" + flowNode.getName() + "'. All names must be unique.");
            } else {
                names.add(flowNode.getName());
            }
            if (flowNode instanceof SubProcessDefinition) {
                final SubProcessDefinition subProcess = (SubProcessDefinition) flowNode;
                validateUniqueNames(subProcess.getSubProcessContainer(), names);
            }
        }
    }

    private void validateProcessAttributes() {
        if (process.getName() != null && process.getName().indexOf(DOUBLE_HYPHEN) != -1) {
            addError("'" + DOUBLE_HYPHEN + "' is fordidden in the process name");
        }
    }

    private void validateActivities(final FlowElementContainerDefinition processContainer) {
        for (final ActivityDefinition activity : processContainer.getActivities()) {
            if (activity instanceof CallActivityDefinition && ((CallActivityDefinition) activity).getCallableElement() == null) {
                addError("The call activity " + activity.getName() + "has a null callable element");
            }
        }

    }

    private void validateGateways(final FlowElementContainerDefinition processContainer) {
        for (final GatewayDefinition gateway : processContainer.getGateways()) {
            for (final TransitionDefinition transition : gateway.getOutgoingTransitions()) {
                switch (gateway.getGatewayType()) {
                    case PARALLEL:
                        if (transition.getCondition() != null) {
                            designErrors.add("The parallel gateway can't have conditional outgoing transitions : " + gateway);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void validateEventsSubProcess() {
        final FlowElementContainerDefinition processContainer = process.getProcessContainer();
        for (final ActivityDefinition activity : processContainer.getActivities()) {
            if (activity instanceof SubProcessDefinition) {
                final FlowElementContainerDefinition subProcessContainer = ((SubProcessDefinition) activity).getSubProcessContainer();

                if (((SubProcessDefinition) activity).isTriggeredByEvent()) {
                    if (subProcessContainer.getStartEvents().size() != 1) {
                        designErrors.add("An event sub process must have one and only one start events, but " + subProcessContainer.getStartEvents().size()
                                + " were found : " + activity);
                    }
                    if (!subProcessContainer.getStartEvents().isEmpty()) {
                        if (subProcessContainer.getStartEvents().get(0).getEventTriggers().isEmpty()) {
                            designErrors.add("The event sub process have no start event with a not NONE trigger : " + activity);
                        }
                    }
                    if (activity.getIncomingTransitions().size() > 0) {
                        designErrors.add("An event sub process cannot have incoming transitions : " + activity);
                    }
                    if (activity.getOutgoingTransitions().size() > 0) {
                        designErrors.add("An event sub process cannot have outgoing transitions : " + activity);
                    }
                }
                validateProcess(subProcessContainer, false);
            }
        }
    }

    private void validateEvents(final FlowElementContainerDefinition flowElementContainer, final boolean isRootContainer) {
        validateStartEvents(flowElementContainer, isRootContainer);
        validateEndEvents(flowElementContainer);
        validateBoundaryEvents(flowElementContainer);
        validateIntermediateCatchEvents(flowElementContainer);
    }

    private void validateIntermediateCatchEvents(final FlowElementContainerDefinition flowElementContainer) {
        for (final IntermediateCatchEventDefinition intermediateCatchEventDefinition : flowElementContainer.getIntermediateCatchEvents()) {
            if (intermediateCatchEventDefinition.getIncomingTransitions().isEmpty()) {
                designErrors.add("An intermediate catch event must have incoming transitions: " + intermediateCatchEventDefinition);
            }
        }
    }

    private void validateEndEvents(final FlowElementContainerDefinition processContainer) {
        for (final EndEventDefinition endEvent : processContainer.getEndEvents()) {
            if (!endEvent.getOutgoingTransitions().isEmpty()) {
                designErrors.add("An end event can't have outgoing transitions: on end event" + endEvent);
            }
        }
    }

    private void validateStartEvents(final FlowElementContainerDefinition processContainer, final boolean isRootContainer) {
        for (final StartEventDefinition startEvent : processContainer.getStartEvents()) {
            if (!startEvent.getIncomingTransitions().isEmpty()) {
                designErrors.add("A start event can't have incoming transitions: on start event" + startEvent);
            }
            validateMessageStartEvent(startEvent, isRootContainer);
        }
    }

    private void validateMessageStartEvent(final StartEventDefinition startEvent, final boolean isRootContainer) {
        if (isRootContainer) {
            final List<CatchMessageEventTriggerDefinition> messageEventTriggers = startEvent.getMessageEventTriggerDefinitions();
            for (final CatchMessageEventTriggerDefinition messageEventTrigger : messageEventTriggers) {
                if (!messageEventTrigger.getCorrelations().isEmpty()) {
                    designErrors.add("A message start event cannot have correlations. On start event:" + startEvent);
                }
            }
        }
    }

    private void validateMultiInstances(final FlowElementContainerDefinition processContainer) {
        for (final ActivityDefinition activity : processContainer.getActivities()) {
            if (activity.getLoopCharacteristics() != null && activity.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics) {
                final MultiInstanceLoopCharacteristics loopCharacteristics = (MultiInstanceLoopCharacteristics) activity.getLoopCharacteristics();
                if (loopCharacteristics.getLoopDataInputRef() != null && !loopCharacteristics.getLoopDataInputRef().isEmpty()
                        && (loopCharacteristics.getLoopDataInputRef() == null || loopCharacteristics.getLoopDataInputRef().isEmpty())) {
                    designErrors.add("The multi instance havs got a data input reference but has not got a loop data input: on activity" + activity.getName());
                }
                if (loopCharacteristics.getDataOutputItemRef() != null && !loopCharacteristics.getDataOutputItemRef().isEmpty()
                        && (loopCharacteristics.getLoopDataOutputRef() == null || loopCharacteristics.getLoopDataOutputRef().isEmpty())) {
                    designErrors.add("The multi instance has got a data output reference but has not got a loop data output: on activity" + activity.getName());
                }
                // TODO add validation on data existence
            }
        }
    }

    private void validateDocuments(final FlowElementContainerDefinition processContainer) {
        for (final DocumentDefinition document : processContainer.getDocumentDefinitions()) {
            if ((document.getFileName() == null || document.getFileName().isEmpty()) && (document.getUrl() == null || document.getUrl().isEmpty())) {
                designErrors.add("A document definition must have a file name or an URL: " + document.getName());
            }
            if (document.getUrl() != null && document.getUrl().length() > 255) {
                designErrors.add("An url can't have more than " + MAX_CHARACTER_URL + " characters.");
            }
            if (document.getFileName() != null && document.getFileName().length() > 255) {
                designErrors.add("A file name can't have more than " + MAX_CHARACTER_FILENAME + " characters.");
            }
        }
    }

    private void validateBoundaryEvents(final FlowElementContainerDefinition processContainer) {
        for (final ActivityDefinition activity : processContainer.getActivities()) {
            if (!activity.getBoundaryEventDefinitions().isEmpty() && !supportAllBoundaryEvents(activity)) {
                for (final BoundaryEventDefinition boundary : activity.getBoundaryEventDefinitions()) {
                    if (!boundary.getTimerEventTriggerDefinitions().isEmpty()) {
                        designErrors.add("Timer boundary events are not supported in automatic, receive and send tasks: " + activity.getName());
                    }
                    if (!boundary.getSignalEventTriggerDefinitions().isEmpty()) {
                        designErrors.add("Signal boundary events are not supported in automatic, receive and send tasks: " + activity.getName());
                    }
                    if (!boundary.getMessageEventTriggerDefinitions().isEmpty()) {
                        designErrors.add("Message boundary events are not supported in automatic, receive and send tasks: " + activity.getName());
                    }
                }
            }
            for (final BoundaryEventDefinition boundaryEvent : activity.getBoundaryEventDefinitions()) {
                if (boundaryEvent.getOutgoingTransitions().isEmpty()) {
                    designErrors.add("A boundary event must have outgoing transitions: " + boundaryEvent.getName());
                } else {
                    validateBoundaryOutgoingTransitions(boundaryEvent);
                }
                if (!boundaryEvent.getIncomingTransitions().isEmpty()) {
                    designErrors.add("A boundary event must not have incoming transitions: " + boundaryEvent.getName());
                }
                if (boundaryEvent.getEventTriggers().isEmpty()) {
                    designErrors.add("A boundary event must have a trigger (it cannot be a NONE event): " + boundaryEvent.getName());
                }
                for (final TimerEventTriggerDefinition timerTigger : boundaryEvent.getTimerEventTriggerDefinitions()) {
                    if (TimerType.CYCLE.equals(timerTigger.getTimerType())) {
                        designErrors.add("Invalid timer type in boundary event " + boundaryEvent.getName() + ": CYCLE is not supported for boundary events.");
                    }
                }
                validateNonInterruptingBoundaryEvent(boundaryEvent);
            }
        }
    }

    private boolean supportAllBoundaryEvents(final ActivityDefinition activity) {
        return !(activity instanceof AutomaticTaskDefinition || activity instanceof ReceiveTaskDefinition || activity instanceof SendTaskDefinition);
    }

    private void validateNonInterruptingBoundaryEvent(final BoundaryEventDefinition boundaryEvent) {
        if (!boundaryEvent.isInterrupting()) {
            if (!boundaryEvent.getSignalEventTriggerDefinitions().isEmpty()) {
                designErrors.add("Non-interrupting boundary events are not supported for SIGNAL events: " + boundaryEvent.getName());
            }
            if (!boundaryEvent.getMessageEventTriggerDefinitions().isEmpty()) {
                designErrors.add("Non-interrupting boundary events are not supported for MESSAGE events: " + boundaryEvent.getName());
            }
            if (!boundaryEvent.getErrorEventTriggerDefinitions().isEmpty()) {
                designErrors.add("An error event must be INTERRUPTING: " + boundaryEvent.getName());
            }
        }
    }

    private void validateBoundaryOutgoingTransitions(final BoundaryEventDefinition boundaryEvent) {
        for (final TransitionDefinition transition : boundaryEvent.getOutgoingTransitions()) {
            if (transition.getCondition() != null) {
                designErrors.add("A boundary event must have not inconditional transitions: " + transition.getSource() + "->" + transition.getTarget());
            }
        }
    }

    void addError(final String error) {
        designErrors.add(error);
    }

    @Override
    public DocumentDefinitionBuilder addDocumentDefinition(final String name) {
        return new DocumentDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name);
    }

    @Override
    public ProcessDefinitionBuilder addDescription(final String description) {
        process.setDescription(description);
        return this;
    }

    public ProcessDefinitionBuilder addDisplayName(final String name) {
        process.setDisplayName(name);
        return this;
    }

    public ProcessDefinitionBuilder addDisplayDescription(final String description) {
        process.setDisplayDescription(description);
        return this;
    }

    void checkExpression(final String context, final Expression e) {
        if (e != null && (e.getContent() == null || e.getContent().isEmpty())) {
            addError("the expression " + e + " in " + context + " has got a null or empty content");
        }
    }

    public void checkName(final String name) {
        if (!isValidName(name)) {
            addError(name + " is not a valid name (it musts respect rules for java identifiers)");
        }

    }

    private boolean isValidName(final String name) {
        // an empty or null string cannot be a valid identifier
        if (name == null || name.length() == 0) {
            return false;
        }
        final char[] c = name.toCharArray();
        if (!Character.isJavaIdentifierStart(c[0])) {
            return false;
        }
        for (int i = 1; i < c.length; i++) {
            if (!Character.isJavaIdentifierPart(c[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ConnectorDefinitionBuilder addConnector(final String name, final String connectorId, final String version, final ConnectorEvent activationEvent) {
        return new ConnectorDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, connectorId, version,
                activationEvent);
    }

    @Override
    public UserTaskDefinitionBuilder addUserTask(final String activityName, final String actorName) {
        return new UserTaskDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), activityName, actorName);
    }

    @Override
    public AutomaticTaskDefinitionBuilder addAutomaticTask(final String activityName) {
        return new AutomaticTaskDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), activityName);
    }

    @Override
    public ReceiveTaskDefinitionBuilder addReceiveTask(final String taskName, final String messageName) {
        return new ReceiveTaskDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), taskName, messageName);
    }

    @Override
    public SendTaskDefinitionBuilder addSendTask(final String taskName, final String messageName, final Expression targetProcess) {
        return new SendTaskDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), taskName, messageName, targetProcess);
    }

    @Override
    public ManualTaskDefinitionBuilder addManualTask(final String name, final String actorName) {
        return new ManualTaskDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, actorName);
    }

    @Override
    public TransitionDefinitionBuilder addTransition(final String source, final String target) {
        return new TransitionDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), source, target, false);
    }

    @Override
    public TransitionDefinitionBuilder addTransition(final String source, final String target, final Expression expression) {
        return new TransitionDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), source, target, expression, false);
    }

    @Override
    public GatewayDefinitionBuilder addGateway(final String name, final GatewayType gatewayType) {
        return new GatewayDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, gatewayType);
    }

    @Override
    public StartEventDefinitionBuilder addStartEvent(final String name) {
        return new StartEventDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name);
    }

    @Override
    public EndEventDefinitionBuilder addEndEvent(final String name) {
        return new EndEventDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name);
    }

    @Override
    public IntermediateCatchEventDefinitionBuilder addIntermediateCatchEvent(final String name) {
        return new IntermediateCatchEventDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name);
    }

    @Override
    public IntermediateThrowEventDefinitionBuilder addIntermediateThrowEvent(final String name) {
        return new IntermediateThrowEventDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name);
    }

    @Override
    public CallActivityBuilder addCallActivity(final String name, final Expression callableElement, final Expression callableElementVersion) {
        return new CallActivityBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, callableElement, callableElementVersion);
    }

    @Override
    public SubProcessActivityDefinitionBuilder addSubProcess(final String name, final boolean triggeredByEvent) {
        return new SubProcessActivityDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, triggeredByEvent);
    }

    @Override
    public DataDefinitionBuilder addIntegerData(final String name, final Expression defaultValue) {
        final String className = Integer.class.getName();
        return new DataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public DataDefinitionBuilder addLongData(final String name, final Expression defaultValue) {
        final String className = Long.class.getName();
        return new DataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public DataDefinitionBuilder addShortTextData(final String name, final Expression defaultValue) {
        final String className = String.class.getName();
        return new TextDataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public TextDataDefinitionBuilder addLongTextData(final String name, final Expression defaultValue) {
        final String className = String.class.getName();
        return new TextDataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue)
                .isLongText();
    }

    @Override
    public DataDefinitionBuilder addDoubleData(final String name, final Expression defaultValue) {
        final String className = Double.class.getName();
        return new DataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public DataDefinitionBuilder addFloatData(final String name, final Expression defaultValue) {
        final String className = Float.class.getName();
        return new DataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public DataDefinitionBuilder addDateData(final String name, final Expression defaultValue) {
        final String className = Date.class.getName();
        return new DataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public XMLDataDefinitionBuilder addXMLData(final String name, final Expression defaultValue) {
        final String className = String.class.getName();
        return new XMLDataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public DataDefinitionBuilder addBlobData(final String name, final Expression defaultValue) {
        final String className = Serializable.class.getName();
        return new DataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public DataDefinitionBuilder addBooleanData(final String name, final Expression defaultValue) {
        final String className = Boolean.class.getName();
        return new DataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    @Override
    public DataDefinitionBuilder addData(final String name, final String className, final Expression defaultValue) {
        return new DataDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), name, className, defaultValue);
    }

    public ActorDefinitionBuilder addActor(final String actorName) {
        return new ActorDefinitionBuilder(this, process, actorName, false);
    }

    public ActorDefinitionBuilder addActor(final String name, final boolean initiator) {
        return new ActorDefinitionBuilder(this, process, name, initiator);
    }

    public ActorDefinitionBuilder setActorInitiator(final String actorName) {
        return new ActorDefinitionBuilder(this, process, actorName, true);
    }

    @Override
    public TransitionDefinitionBuilder addDefaultTransition(final String source, final String target) {
        return new TransitionDefinitionBuilder(this, (FlowElementContainerDefinitionImpl) process.getProcessContainer(), source, target, null, true);
    }

    /**
     * validate the process consistency and return it
     * 
     * @return
     *         the process being build
     * @throws InvalidProcessDefinitionException
     *             when the process definition is inconsistent. The exception contains causes
     */
    public DesignProcessDefinition getProcess() throws InvalidProcessDefinitionException {
        return done();
    }

}
