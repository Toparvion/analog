package tech.toparvion.analog.service;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.util.Assert;
import org.springframework.web.client.*;

import java.io.IOException;
import java.net.URI;

import static java.util.List.of;

/**
 * A {@link RestTemplate} implementation aimed exclusively at using in streaming download scenario (when entity
 * gained from remote site is not buffered but streamed further). The main feature of this implementation is that it
 * does not close response's InputStream upon return of
 * {@link #doExecute(URI, HttpMethod, RequestCallback, ResponseExtractor) doExecute} method. It implicitly relies on
 * passing the stream to {@link ResourceHttpMessageConverter} which will close it after transferring to the client at
 * its {@code writeContent} method. The passing is provided by {@link tech.toparvion.analog.controller.DownloadController}.<br/>
 * Whilst this is obviously not the only and probably not the most reliable solution, it makes transitive streaming
 * come true with minimal efforts.
 *
 * @implNote Because this class is designed to be used in aforementioned scenario (and only there) it is not
 * recommended to use it anywhere else as it may lead to incorrect resource handling (memory exhaustion, in particular).
 *
 *
 * @author Toparvion
 * @since v0.10
 */
public class DownloadRestTemplate extends RestTemplate {

  public DownloadRestTemplate() {
    super(of(new ResourceHttpMessageConverter(true)));
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setBufferRequestBody(false);   // to make restTemplate use SimpleStreamingClientHttpRequest
    this.setRequestFactory(requestFactory);
  }

  @Override
  protected <T> T doExecute(URI url,
                            HttpMethod method,
                            RequestCallback requestCallback,
                            ResponseExtractor<T> responseExtractor) throws RestClientException {
    Assert.notNull(url, "'url' must not be null");
    Assert.notNull(method, "'method' must not be null");
    ClientHttpResponse response;
    try {
      ClientHttpRequest request = createRequest(url, method);
      if (requestCallback != null) {
        requestCallback.doWithRequest(request);
      }
      response = request.execute();
      handleResponse(url, method, response);
      if (responseExtractor != null) {
        return responseExtractor.extractData(response);
      }
      else {
        return null;
      }
    }
    catch (IOException ex) {
      String resource = url.toString();
      String query = url.getRawQuery();
      resource = (query != null ? resource.substring(0, resource.indexOf('?')) : resource);
      throw new ResourceAccessException("I/O error on " + method.name() +
          " request for \"" + resource + "\": " + ex.getMessage(), ex);
    }
    // Here is the only difference of this method from its parent - we deliberately don't close response input stream
    // because we definitely know (1) it wasn't read yet, (2) it will be read later and (3) it will be closed
    // immediately after reading (at org.springframework.http.converter.ResourceHttpMessageConverter:139)
//        finally {
//          if (response != null) {
//            response.close();
//          }
//        }

  }
}
