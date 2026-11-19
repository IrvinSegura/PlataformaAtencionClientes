package com.irvinsegura.supportchat.actions.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.actions.domain.model.EndpointNotAllowedException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class NetworkEndpointPolicyTest {

    private final NetworkEndpointPolicy strict = new NetworkEndpointPolicy(false, false);

    @Test
    void webhooksMustUseHttps() {
        assertThatThrownBy(() -> strict.check("http://93.184.215.14/orders"))
                .isInstanceOf(EndpointNotAllowedException.class);
    }

    @Test
    void privateAndLocalAddressesAreRefused() {
        for (String url : new String[] {"https://127.0.0.1/x", "https://10.0.0.5/x", "https://192.168.1.10/x",
                "https://169.254.169.254/latest/meta-data", "https://[::1]/x", "https://100.64.0.1/x"}) {
            assertThatThrownBy(() -> strict.check(url)).as(url).isInstanceOf(EndpointNotAllowedException.class);
        }
    }

    @Test
    void publicAddressesAreAllowed() throws UnknownHostException {
        assertThatCode(() -> strict.check("https://93.184.215.14/orders")).doesNotThrowAnyException();
        assertThat(NetworkEndpointPolicy.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(NetworkEndpointPolicy.isPublic(InetAddress.getByName("::ffff:10.0.0.1"))).isFalse();
        assertThat(NetworkEndpointPolicy.isPublic(InetAddress.getByName("fd00::1"))).isFalse();
    }

    @Test
    void developmentMayReachLocalHttpServers() {
        assertThatCode(() -> new NetworkEndpointPolicy(true, true).check("http://localhost:8081/hook"))
                .doesNotThrowAnyException();
    }
}
