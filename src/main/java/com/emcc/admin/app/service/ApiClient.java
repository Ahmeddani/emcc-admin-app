package com.emcc.admin.app.service;

import com.emcc.admin.app.model.Asset;
import com.emcc.admin.app.model.Member;
import com.emcc.admin.app.model.Payment;
import com.emcc.admin.app.model.PlaidConnectInfo;
import com.emcc.admin.app.model.UnmatchedPayment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ApiClient {

    // Port 8080 is used by another project (comeme-backend) on this machine, so
    // emcc-admin-backend runs on 8081 instead (see run.sh). Keep these in sync.
    private static final String BASE_URL = "http://localhost:8081/api";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String authToken;
    private static String adminName;
    private static boolean mustChangePassword;

    /** True after login when the admin is still using an assigned password. */
    public static boolean mustChangePassword() {
        return mustChangePassword;
    }

    public static String getAdminName() {
        return adminName;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /** Result of the password step: either signed in, or a code was emailed and must be verified. */
    public record LoginChallenge(String challengeId, String sentTo) {}

    /**
     * Step 1: checks email + password. Returns null if signed in directly (MFA disabled on
     * the server), or a challenge when a sign-in code has been emailed.
     */
    public static LoginChallenge login(String email, String password) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", password);

        HttpResponse<String> response = postAuth("/auth/login", body);
        if (response.statusCode() == 200) {
            JsonNode json = MAPPER.readTree(response.body());
            if (json.path("mfaRequired").asBoolean(false)) {
                return new LoginChallenge(json.get("challengeId").asText(), json.path("sentTo").asText(""));
            }
            storeSession(json);
            return null;
        } else if (response.statusCode() == 401) {
            throw new IllegalArgumentException("Invalid email or password.");
        } else {
            throw new IllegalArgumentException(errorMessage(response,
                    "Login failed with status " + response.statusCode()));
        }
    }

    /** Step 2: verifies the emailed code and stores the session token. */
    public static void verifyLoginCode(String challengeId, String code) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("challengeId", challengeId);
        body.put("code", code);

        HttpResponse<String> response = postAuth("/auth/verify", body);
        if (response.statusCode() == 200) {
            storeSession(MAPPER.readTree(response.body()));
        } else if (response.statusCode() == 410) {
            throw new IllegalStateException(errorMessage(response, "Code expired. Sign in again."));
        } else {
            throw new IllegalArgumentException(errorMessage(response, "Incorrect code."));
        }
    }

    /** Emails a new code for the same challenge (server enforces a 30 s cooldown). */
    public static void resendLoginCode(String challengeId) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("challengeId", challengeId);
        HttpResponse<String> response = postAuth("/auth/resend", body);
        if (response.statusCode() != 200) {
            throw new IllegalArgumentException(errorMessage(response, "Could not resend the code."));
        }
    }

    private static HttpResponse<String> postAuth(String path, ObjectNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void storeSession(JsonNode json) {
        authToken = json.get("token").asText();
        adminName = json.path("adminName").asText("Admin");
        mustChangePassword = json.path("mustChangePassword").asBoolean(false);
    }

    /** Replaces the signed-in admin's password. Throws IllegalArgumentException with the server's reason. */
    public static void changePassword(String currentPassword, String newPassword) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/change-password"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalArgumentException(errorMessage(response, "Could not change the password."));
        }
        mustChangePassword = false;
    }

    private static String errorMessage(HttpResponse<String> response, String fallback) {
        try {
            String msg = MAPPER.readTree(response.body()).path("error").asText("");
            return msg.isBlank() ? fallback : msg;
        } catch (Exception e) {
            return fallback;
        }
    }

    // ── Members ───────────────────────────────────────────────────────────────

    public static List<Member> getMembers() throws Exception {
        JsonNode array = get("/members");
        List<Member> list = new ArrayList<>();
        for (JsonNode node : array) list.add(memberFromJson(node));
        return list;
    }

    public static Member saveMember(Member member) throws Exception {
        ObjectNode body = memberToJson(member);
        JsonNode result = member.isPersisted()
                ? put("/members/" + member.getMembershipId(), body)
                : post("/members", body);
        Member saved = memberFromJson(result);
        member.setPersisted(true);
        return saved;
    }

    public static void deleteMember(String membershipId) throws Exception {
        delete("/members/" + membershipId);
    }

    // ── Assets ────────────────────────────────────────────────────────────────

    public static List<Asset> getAssets() throws Exception {
        JsonNode array = get("/assets");
        List<Asset> list = new ArrayList<>();
        for (JsonNode node : array) list.add(assetFromJson(node));
        return list;
    }

    public static Asset saveAsset(Asset asset) throws Exception {
        ObjectNode body = assetToJson(asset);
        JsonNode result = (asset.getId() != null)
                ? put("/assets/" + asset.getId(), body)
                : post("/assets", body);
        return assetFromJson(result);
    }

    public static void deleteAsset(Long id) throws Exception {
        delete("/assets/" + id);
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    public static List<Payment> getPayments() throws Exception {
        JsonNode array = get("/payments");
        List<Payment> list = new ArrayList<>();
        for (JsonNode node : array) list.add(paymentFromJson(node));
        return list;
    }

    public static Payment savePayment(Payment payment) throws Exception {
        ObjectNode body = paymentToJson(payment);
        JsonNode result = (payment.getId() != null)
                ? put("/payments/" + payment.getId(), body)
                : post("/payments", body);
        return paymentFromJson(result);
    }

    public static void deletePayment(Long id) throws Exception {
        delete("/payments/" + id);
    }

    // ── Plaid ─────────────────────────────────────────────────────────────────

    public static PlaidConnectInfo getPlaidConnectInfo() throws Exception {
        JsonNode json = get("/plaid/link-token");
        return new PlaidConnectInfo(json.get("connectUrl").asText(), json.path("env").asText("sandbox"));
    }

    public static boolean isPlaidConnected() throws Exception {
        JsonNode json = get("/plaid/status");
        return json.path("connected").asBoolean(false);
    }

    public static String getConnectedInstitution() throws Exception {
        JsonNode json = get("/plaid/status");
        return json.path("institution").asText("");
    }

    public static int[] syncPayments() throws Exception {
        JsonNode json = post("/plaid/sync", MAPPER.createObjectNode());
        return new int[]{
                json.path("matched").asInt(0),
                json.path("unmatched").asInt(0),
                json.path("alreadyProcessed").asInt(0)
        };
    }

    public static List<UnmatchedPayment> getUnmatchedPayments() throws Exception {
        JsonNode array = get("/plaid/unmatched");
        List<UnmatchedPayment> list = new ArrayList<>();
        for (JsonNode n : array) {
            String amount = n.path("amount").isMissingNode() ? "" : "$" + String.format("%.2f", n.path("amount").asDouble());
            list.add(new UnmatchedPayment(
                    n.path("id").asLong(),
                    n.path("description").asText(""),
                    amount,
                    n.path("date").asText("")
            ));
        }
        return list;
    }

    public static void assignUnmatched(Long id, String memberId, String memberName) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("memberId", memberId);
        body.put("memberName", memberName);
        post("/plaid/unmatched/" + id + "/assign", body);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private static JsonNode get(String path) throws Exception {
        HttpRequest req = authorized(HttpRequest.newBuilder().uri(URI.create(BASE_URL + path)).GET()).build();
        return send(req);
    }

    private static JsonNode post(String path, Object body) throws Exception {
        HttpRequest req = authorized(HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))).build();
        return send(req);
    }

    private static JsonNode put(String path, Object body) throws Exception {
        HttpRequest req = authorized(HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))).build();
        return send(req);
    }

    private static void delete(String path) throws Exception {
        HttpRequest req = authorized(HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .DELETE()).build();
        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Delete failed: HTTP " + response.statusCode());
        }
    }

    private static HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        if (authToken != null) builder.header("Authorization", "Bearer " + authToken);
        return builder;
    }

    private static JsonNode send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Request failed: HTTP " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    // ── Type conversions ──────────────────────────────────────────────────────

    private static Member memberFromJson(JsonNode n) {
        String monthlyDues = (!n.path("monthlyDues").isMissingNode() && !n.path("monthlyDues").isNull())
                ? String.format("%.2f", n.path("monthlyDues").asDouble())
                : "";
        Member m = new Member(
                n.path("membershipId").asText(""),
                n.path("name").asText(""),
                n.path("address").asText(""),
                n.path("phoneNumber").asText(""),
                n.path("email").asText(""),
                n.path("maritalStatus").asText("Single"),
                String.valueOf(n.path("childrenUnder10").asInt(0)),
                String.valueOf(n.path("children10To18").asInt(0)),
                n.path("occupation").asText(""),
                n.path("createdDate").asText(""),
                monthlyDues
        );
        m.setPersisted(true);
        return m;
    }

    private static ObjectNode memberToJson(Member m) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("membershipId", m.getMembershipId());
        n.put("name", m.getName());
        n.put("address", nullToEmpty(m.getAddress()));
        n.put("phoneNumber", nullToEmpty(m.getPhoneNumber()));
        n.put("email", nullToEmpty(m.getEmail()));
        n.put("maritalStatus", nullToEmpty(m.getMaritalStatus()));
        n.put("childrenUnder10", parseIntSafe(m.getChildrenUnder10()));
        n.put("children10To18", parseIntSafe(m.getChildren10To18()));
        n.put("occupation", nullToEmpty(m.getOccupation()));
        if (m.getCreatedDate() != null && !m.getCreatedDate().isEmpty()) n.put("createdDate", m.getCreatedDate());
        String dues = m.getMonthlyDues() == null ? "" : m.getMonthlyDues().trim();
        if (!dues.isEmpty()) {
            try { n.put("monthlyDues", Double.parseDouble(dues)); } catch (NumberFormatException e) { n.putNull("monthlyDues"); }
        } else {
            n.putNull("monthlyDues");
        }
        return n;
    }

    private static Asset assetFromJson(JsonNode n) {
        Asset a = new Asset(
                n.path("assetName").asText(""),
                n.path("type").asText(""),
                String.valueOf(n.path("count").asInt(0)),
                n.path("condition").asText("")
        );
        if (!n.path("id").isMissingNode() && !n.path("id").isNull()) a.setId(n.path("id").asLong());
        return a;
    }

    private static ObjectNode assetToJson(Asset a) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("assetName", nullToEmpty(a.getAssetName()));
        n.put("type", nullToEmpty(a.getType()));
        n.put("count", parseIntSafe(a.getCount()));
        n.put("condition", nullToEmpty(a.getCondition()));
        return n;
    }

    private static Payment paymentFromJson(JsonNode n) {
        String amount = "";
        if (!n.path("amount").isMissingNode() && !n.path("amount").isNull()) {
            amount = "$" + String.format("%.2f", n.path("amount").asDouble());
        }
        Payment p = new Payment(
                n.path("memberId").asText(""),
                n.path("memberName").asText(""),
                amount,
                n.path("status").asText("Pending"),
                n.path("date").asText(""),
                n.path("paymentMethod").asText(""),
                n.path("invoiceNumber").asText("")
        );
        if (!n.path("id").isMissingNode() && !n.path("id").isNull()) p.setId(n.path("id").asLong());
        return p;
    }

    private static ObjectNode paymentToJson(Payment p) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("memberId", nullToEmpty(p.getMemberId()));
        n.put("memberName", nullToEmpty(p.getMemberName()));
        String amtStr = p.getAmount() == null ? "" : p.getAmount().replace("$", "").trim();
        try { n.put("amount", Double.parseDouble(amtStr)); } catch (Exception e) { n.putNull("amount"); }
        n.put("status", p.getStatus() == null ? "Pending" : p.getStatus());
        if (p.getDate() != null && !p.getDate().isEmpty()) n.put("date", p.getDate());
        n.put("paymentMethod", nullToEmpty(p.getPaymentMethod()));
        n.put("invoiceNumber", nullToEmpty(p.getInvoiceNumber()));
        return n;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static int parseIntSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
