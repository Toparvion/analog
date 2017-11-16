package tech.toparvion.analog.remote;

/**
 * Some constants which used commonly for both server and agent sides.<p>
 * These constants also serve as links for IDE navigation among Spring Integration beans as they have no other
 * fixed (compile-time) links except these values.<p>
 * The names of all constants are supposed to be self-describable so that the class itself may be imported on demand
 * (e.g. like <br/>{@code import static tech.toparvion.analog.remote.RemotingConstants.*}).
 * @author Toparvion
 * @since v0.7
 */
public final class RemotingConstants {
  private RemotingConstants(){}

  //<editor-fold desc="Channels names">
  public static final String SERVER_REGISTRATION_ROUTER__CHANNEL = "serverRegistrationRouterChannel";
  public static final String SERVER_REGISTRATION_RMI_OUT__CHANNEL_PREFIX = "serverRegistrationRmiOutChannel_";
  public static final String AGENT_REGISTRATION_RMI_IN__CHANNEL = "agentRegistrationRmiInChannel";
  public static final String SERVER_RMI_PAYLOAD_IN__CHANNEL = "serverRmiPayloadInChannel";
  //</editor-fold>

  //<editor-fold desc="Server-side headers names">
  public static final String REGISTRATION_MODE__HEADER = "registrationMode";
  public static final String REPLY_ADDRESS__HEADER = "replyAddress";
  public static final String LOG_TIMESTAMP_VALUE__HEADER = "logTimestampValue";
  public static final String RECORD_LEVEL__HEADER = "recordLevel";
  public static final String SEQUENCE_NUMBER__HEADER = "sequenceNumberLong";
  public static final String LOG_CONFIG_ENTRY_UID__HEADER = "uid";
  public static final String SOURCE_NODE__HEADER = "sourceNode";
  //</editor-fold>

  //<editor-fold desc="Websocket">
  public static final String WEBSOCKET_TOPIC_PREFIX = "/topic/";
  public static final String WEBSOCKET_APP_PREFIX = "/app";
  public static final String WEBSOCKET_ENDPOINT = "/watch-endpoint";
  public static final String MESSAGE_TYPE_HEADER = "type";
  public enum MessageType { RECORD, METADATA, FAILURE }
  //</editor-fold>
}
