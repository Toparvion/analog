/**
 * 'Origin' in terms of AnaLog is a tool that serves as a log source. As of v0.11 there are only 3 origins known to
 * AnaLog: tail utility, Docker client (docker) and Kubernetes client (kubectl).<p>
 *   
 * This package contains non-public '-Recognizer' classes capable of mapping tail events' texts into AnaLog internal
 * {@link tech.toparvion.analog.model.LogEventType types}, as well as single public 
 * {@link tech.toparvion.analog.service.origin.LogEventTypeDetector LogEventTypeDetector} which employs recognizers 
 * to detect various log event types.
 *
 * @author Toparvion
 * @since 0.11
 */
package tech.toparvion.analog.service.origin;