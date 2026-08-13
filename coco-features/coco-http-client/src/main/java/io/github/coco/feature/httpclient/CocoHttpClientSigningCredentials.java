package io.github.coco.feature.httpclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 命名 HTTP 客户端签名凭据决议器。
 *
 * @author patton174
 * @since 1.0.0
 */
final class CocoHttpClientSigningCredentials {

    private CocoHttpClientSigningCredentials() {
    }

    static CocoHttpClientSigningCredential resolve(String clientName,
            CocoHttpClientProperties.Signing signing,
            List<Provider> providers) {
        List<Candidate> matches = new ArrayList<>();
        for (Provider provider : providers) {
            Optional<CocoHttpClientSigningCredential> credential = resolve(provider, clientName);
            credential.ifPresent(value -> matches.add(new Candidate(provider.name(), value)));
        }
        if (matches.size() > 1) {
            String names = matches.stream().map(Candidate::providerName).distinct().sorted()
                    .reduce((left, right) -> left + ", " + right).orElse("unknown");
            throw new IllegalStateException("Multiple Coco HTTP client signing credential providers matched client '"
                    + clientName + "': " + names);
        }
        if (matches.size() == 1) {
            return matches.get(0).credential();
        }
        try {
            return signing.credential();
        }
        catch (RuntimeException ex) {
            throw new IllegalStateException("coco.http.clients." + clientName
                    + ".signing credential is required");
        }
    }

    private static Optional<CocoHttpClientSigningCredential> resolve(
            Provider provider, String clientName) {
        try {
            Optional<CocoHttpClientSigningCredential> credential = provider.delegate().resolve(clientName);
            return credential == null ? Optional.empty() : credential;
        }
        catch (RuntimeException ex) {
            throw new IllegalStateException("Coco HTTP client signing credential provider '"
                    + provider.name() + "' failed for client '" + clientName + "'");
        }
    }

    record Provider(String name, CocoHttpClientSigningCredentialProvider delegate) {
    }

    private record Candidate(String providerName, CocoHttpClientSigningCredential credential) {
    }
}
