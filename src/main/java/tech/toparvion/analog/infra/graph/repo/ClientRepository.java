package tech.toparvion.analog.infra.graph.repo;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.transaction.annotation.Transactional;
import tech.toparvion.analog.infra.graph.model.Client;

/**
 * @author Toparvion
 * @since v0.14
 */
public interface ClientRepository extends Neo4jRepository<Client, Long> {

  @Transactional
  Long deleteClientByIp(String ip);
  
}
