package dev.yeonwoo.chipthrone.quote.client;

import dev.yeonwoo.chipthrone.quote.model.KisAccessToken;

public interface KisTokenClient {

    KisAccessToken issueToken();
}
