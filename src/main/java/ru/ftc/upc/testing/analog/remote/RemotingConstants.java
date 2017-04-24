package ru.ftc.upc.testing.analog.remote;

/**
 * Some constants which used commonly for both server and agent sides.<p>
 * These constants also serve as links for IDE navigation among Spring Integration beans as they have no other
 * fixed (compile-time) links except these values.<p>
 * The names of all constants are supposed to be self-describable so that the class itself may be imported on demand
 * (e.g. like <br/>{@code import static ru.ftc.upc.testing.analog.remote.RemotingConstants.*}).
 * @author Toparvion
 * @since v0.7
 */
public final class RemotingConstants {

  //<editor-fold desc="Channels names">
  public static final String SERVER_REGISTRATION_RMI_OUT__CHANNEL = "serverRegistrationRmiOutChannel";
  public static final String AGENT_REGISTRATION_RMI_IN__CHANNEL = "agentRegistrationRmiInChannel";
  public static final String SERVER_RMI_PAYLOAD_IN__CHANNEL = "serverRmiPayloadInChannel";
  //</editor-fold>


  //<editor-fold desc="Headers names">
  public static final String REGISTRATION_MODE__HEADER = "registrationMode";
  public static final String SENDER_ADDRESS__HEADER = "senderAddress";
  public static final String LOG_TIMESTAMP_FORMAT__HEADER = "logTimestampFormat";
  public static final String LOG_TIMESTAMP_VALUE__HEADER = "logTimestampValue";
  public static final String RECORD_LEVEL__HEADER = "recordLevel";
  public static final String SEQUENCE_NUMBER__HEADER = "sequenceNumberLong";
  public static final String RECORD_AGGREGATOR_INPUT_CHANNEL = "recordAggregatorInputChannel";
  //</editor-fold>
}
