package com.myfinance.service;

import com.myfinance.model.Account;
import com.myfinance.model.Owner;
import com.myfinance.model.enums.Broker;
import com.tigerbrokers.stock.openapi.client.config.ClientConfig;
import com.tigerbrokers.stock.openapi.client.https.client.TigerHttpClient;
import com.tigerbrokers.stock.openapi.client.https.domain.trade.item.TradeOrder;
import com.tigerbrokers.stock.openapi.client.https.request.trade.QueryOrderRequest;
import com.tigerbrokers.stock.openapi.client.https.response.trade.BatchOrderResponse;
import com.tigerbrokers.stock.openapi.client.struct.enums.Language;
import com.tigerbrokers.stock.openapi.client.struct.enums.MethodName;
import com.tigerbrokers.stock.openapi.client.struct.enums.SecType;
import com.tigerbrokers.stock.openapi.client.util.builder.AccountParamBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Live trade sync for Tiger Brokers via the RSA-signed Tiger Open API.
 *
 * <p>Credentials are loaded (decrypted) from the account's stored {@link Broker#TIGER} configuration:
 * meta1 = Tiger developer id, meta2 = trading account, secret1 = PKCS#8 RSA private key. The private
 * key stays inside {@link BrokerCredentialService} / this call and is never logged.
 *
 * <p>Filled orders are mapped into the common {@link IbkrTradeParser.FlexTrades} shape by
 * {@link TigerTradeMapper} and then run through {@link IbkrSyncService}'s existing preview/apply so
 * de-duplication, holdings and realized P&amp;L behave identically to the IBKR path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TigerSyncService {

    private final BrokerCredentialService brokerCredentialService;
    private final TigerTradeMapper tigerTradeMapper;
    private final IbkrSyncService ibkrSyncService;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Resolved Tiger credentials for one account (secret kept only for the duration of the call). */
    private record TigerCreds(String tigerId, String account, String privateKey) {}

    /** Preview a Tiger trade sync — fetch filled orders and classify them, writing nothing. */
    public IbkrSyncService.SyncPreview preview(Long userId, Account account, Owner owner,
                                               LocalDate from, LocalDate to) {
        IbkrTradeParser.FlexTrades parsed = tigerTradeMapper.toFlexTrades(fetchFilledOrders(userId, account.getId(), from, to));
        return ibkrSyncService.previewTrades(parsed, userId, account, owner, from, to);
    }

    /** Apply a Tiger trade sync — insert new trades, overwrite approved mismatches, recompute P&amp;L. */
    public IbkrSyncService.SyncResult apply(Long userId, Account account, Owner owner,
                                            LocalDate from, LocalDate to, Set<String> approvedMismatchTradeIds) {
        IbkrTradeParser.FlexTrades parsed = tigerTradeMapper.toFlexTrades(fetchFilledOrders(userId, account.getId(), from, to));
        return ibkrSyncService.applyTrades(parsed, userId, account, owner, from, to, approvedMismatchTradeIds);
    }

    // ─────────────────────────── Tiger Open API ───────────────────────────

    /**
     * Call the Tiger Open API for the account's filled orders in the given window. Tiger requires
     * both a start and end date, so we default an open-ended range to a wide window ending today.
     */
    List<TradeOrder> fetchFilledOrders(Long userId, Long accountId, LocalDate from, LocalDate to) {
        TigerCreds creds = resolveCreds(userId, accountId);
        TigerHttpClient client = buildClient(creds);

        LocalDate start = from != null ? from : LocalDate.now().minusYears(5);
        LocalDate end = to != null ? to : LocalDate.now();

        List<TradeOrder> all = new ArrayList<>();
        String pageToken = null;
        int guard = 0; // hard stop against a misbehaving pagination loop
        try {
            do {
                AccountParamBuilder params = AccountParamBuilder.instance()
                        .account(creds.account())
                        .secType(SecType.STK)
                        .startDate(start.atStartOfDay().format(TS))
                        .endDate(end.atTime(LocalTime.MAX).format(TS))
                        .limit(300)
                        .lang(Language.en_US);
                if (pageToken != null) params.pageToken(pageToken);

                QueryOrderRequest request = new QueryOrderRequest(MethodName.FILLED_ORDERS);
                request.setBizContent(params.buildJson());

                BatchOrderResponse response = client.execute(request);
                if (response == null || !response.isSuccess()) {
                    String msg = response == null ? "no response" : response.getMessage();
                    throw new RuntimeException("Tiger rejected the request: " + msg);
                }
                if (response.getItem() == null || response.getItem().getOrders() == null) break;
                all.addAll(response.getItem().getOrders());
                pageToken = response.getItem().getNextPageToken();
            } while (pageToken != null && !pageToken.isBlank() && ++guard < 50);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not reach the Tiger Open API: " + e.getMessage(), e);
        }
        log.info("Tiger filled-orders fetch for userId={} account={}: {} orders", userId, accountId, all.size());
        return all;
    }

    /** Build a per-call Tiger client from the decrypted credentials. */
    private TigerHttpClient buildClient(TigerCreds creds) {
        ClientConfig config = new ClientConfig();
        config.tigerId = creds.tigerId();
        config.defaultAccount = creds.account();
        config.privateKey = creds.privateKey();
        return TigerHttpClient.getInstance().clientConfig(config);
    }

    /** Load + decrypt the account's Tiger credential, validating that everything required is present. */
    private TigerCreds resolveCreds(Long userId, Long accountId) {
        var cred = brokerCredentialService.decryptFor(userId, accountId, Broker.TIGER)
                .orElseThrow(() -> new RuntimeException(
                        "No Tiger credentials saved for this account — add them on the Account page"));
        String tigerId = cred.meta1();
        String account = cred.meta2();
        String privateKey = cred.secret1();
        if (isBlank(tigerId) || isBlank(account) || isBlank(privateKey)) {
            throw new RuntimeException("Tiger credentials are incomplete — set the Tiger ID, account and private key on the Account page");
        }
        return new TigerCreds(tigerId.trim(), account.trim(), privateKey.trim());
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
