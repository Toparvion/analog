package tech.toparvion.analog.remote.agent.origin.adapt;

import org.springframework.beans.factory.InitializingBean;
import tech.toparvion.analog.model.config.adapters.GeneralAdapterParams;

/**
 * @author Toparvion
 * @since 0.11
 */
public interface OriginAdapter extends InitializingBean {

  GeneralAdapterParams adapterParams();

}
