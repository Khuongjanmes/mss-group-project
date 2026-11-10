package com.openevent.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Dữ liệu và chữ ký mẫu lấy từ tài liệu payOS (payos.vn/docs, mục kiểm tra dữ liệu với signature). */
class PayOsGatewayTest {

    private static final String DOC_CHECKSUM_KEY = "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
    private static final String DOC_SIGNATURE = "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03";
    private static final String DOC_WEBHOOK = """
            {"code":"00","desc":"success","success":true,"data":{"orderCode":123,"amount":3000,
            "description":"VQRIO123","accountNumber":"12345678","reference":"TF230204212323",
            "transactionDateTime":"2023-02-04 18:25:00","currency":"VND",
            "paymentLinkId":"124c33293c43417ab7879e14c8d9eb18","code":"00","desc":"Thành công",
            "counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"",
            "counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""},
            "signature":"%s"}""".formatted(DOC_SIGNATURE);

    private final JsonMapper json = JsonMapper.builder().build();
    private final PayOsGateway payOs = new PayOsGateway(new PaymentProperties("http://spa.test", null,
            new PaymentProperties.PayOs("client", "key", DOC_CHECKSUM_KEY, null)), json);

    @Test
    void signatureMatchesPayOsDocumentation() {
        assertThat(payOs.signData(json.readTree(DOC_WEBHOOK).path("data"))).isEqualTo(DOC_SIGNATURE);
    }

    @Test
    void verifiedWebhookIsReadAndTamperedWebhookIsRejected() {
        assertThat(payOs.verifyWebhook(json.readTree(DOC_WEBHOOK)))
                .contains(new PayOsGateway.Notice("123", 3000, true));

        JsonNode tampered = json.readTree(DOC_WEBHOOK);
        ((ObjectNode) tampered.path("data")).put("amount", 3);
        assertThat(payOs.verifyWebhook(tampered)).isEmpty();
        assertThat(payOs.verifyWebhook(json.readTree("{\"data\":{}}"))).isEmpty();
    }

    @Test
    void nullValuesAreSignedAsEmptyStrings() {
        JsonNode withNull = json.readTree("{\"b\":null,\"a\":1}");
        JsonNode withEmpty = json.readTree("{\"a\":1,\"b\":\"\"}");

        assertThat(payOs.signData(withNull)).isEqualTo(payOs.signData(withEmpty));
    }
}
