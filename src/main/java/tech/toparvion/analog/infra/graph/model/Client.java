package tech.toparvion.analog.infra.graph.model;

import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

import java.time.Instant;

/**
 * AnaLog client (usually a web browser). Because there are no browser unique ID, the client is identified with its 
 * IP address. It means that there can be multiple watching sessions from the same client if the client runs multiple 
 * browsers at the same time.    
 * 
 * @author Toparvion
 * @since v0.14
 */
@NodeEntity
public class Client {
  @Id
  @GeneratedValue
  private Long id;
  
  @Property("ip")
  private String ip;
  
  @Property("since")
  private Instant since;

  public Client(String ip, Instant since) {
    this.ip = ip;
    this.since = since;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public Instant getSince() {
    return since;
  }

  public void setSince(Instant since) {
    this.since = since;
  }

  public Long getId() {
    return id;
  }
}
