/**
 * 'Origin' in terms of AnaLog is a tool playing a rolse of log source. As of v0.11 there only 3 origins known to
 * AnaLog: tail utility, Docker client (dcoker) and  Kubernetenes client (kubectl). <p>
 * This part of origin facilities concerns detection of log event types, e.g. log absence, rotation and so on.
 *
 * @author Toparvion
 * @since 0.11
 */
package tech.toparvion.analog.remote.server.origin.detect;