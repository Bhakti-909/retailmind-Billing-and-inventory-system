package com.smartretail.billing.service;

import com.smartretail.billing.entity.BusinessProfile;
import com.smartretail.billing.entity.Invoice;
import com.smartretail.billing.entity.SaleItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class InvoicePdfService {
    // Match the 78 mm thermal receipt used by the Billing screen's print
    // stylesheet.
    private static final float PAGE_WIDTH = 78f * 72f / 25.4f;
    private static final float SIDE_MARGIN = 12f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * SIDE_MARGIN;
    private static final float LINE_HEIGHT = 11f;
    private static final Color INK = new Color(31, 41, 55);
    private static final Color MUTED = new Color(91, 102, 116);
    private static final Color BRAND = new Color(15, 118, 110);
    private static final Color RULE = new Color(183, 194, 202);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    public byte[] create(BusinessProfile business, Invoice invoice, String customerPhone, String customerAddress,
            String customerEmail) throws IOException {
        String shop = value(business.getShopName(), "Smart Retail");
        List<ReceiptRow> rows = new ArrayList<>();

        rows.add(ReceiptRow.center(initials(shop), 10, true, BRAND, 15));
        rows.addAll(ReceiptRow.wrapped(shop, 11, true, INK, CONTENT_WIDTH, true));
        addWrapped(rows, address(business), 7.5f, false, MUTED, true);
        if (invoice.isGstInvoice())
            addWrapped(rows, "GSTIN: " + value(invoice.getSellerGstin(), "-"), 7.5f, false, MUTED, true);
        rows.add(ReceiptRow.spacer(4));
        rows.add(ReceiptRow.rule());
        rows.add(ReceiptRow.center(invoice.isGstInvoice() ? "GST TAX INVOICE" : "RETAIL RECEIPT", 9, true, BRAND,
                LINE_HEIGHT + 1));
        rows.add(ReceiptRow.pair("Invoice", value(invoice.getInvoiceNumber(), "-"), 8, false));
        rows.add(ReceiptRow.pair("Date", invoice.getSoldAt().format(DATE), 8, false));
        rows.add(ReceiptRow.pair("Customer", value(invoice.getBuyerName(), "Walk-in customer"), 8, false));
        addOptionalPair(rows, "Phone", customerPhone);
        addOptionalPair(rows, "Email", customerEmail);
        addOptionalPair(rows, "Address", customerAddress);
        rows.add(ReceiptRow.pair("Payment mode", invoice.getPaymentMethod(), 8, false));
        rows.add(ReceiptRow.pair("Payment status", invoice.getBalanceDue().signum() > 0 ? "Balance due" : "Paid", 8,
                true));
        if (invoice.getBalanceDue().signum() > 0) {
            rows.add(ReceiptRow.pair("Balance due", money(invoice.getBalanceDue()), 8, true));
        }
        if (invoice.getPaymentDueAt() != null) {
            rows.addAll(ReceiptRow.wrapped("Payment due: " + invoice.getPaymentDueAt()
                    .atZone(ZoneId.of("Asia/Kolkata")).format(DATE) + " IST", 7.5f, false, MUTED,
                    CONTENT_WIDTH, false));
        }
        if (invoice.isGstInvoice() && invoice.getBuyerGstin() != null && !invoice.getBuyerGstin().isBlank()) {
            addOptionalPair(rows, "Buyer GSTIN", invoice.getBuyerGstin());
        }
        if ("Split".equalsIgnoreCase(invoice.getPaymentMethod())) {
            addWrapped(rows, "Payment split: " + paymentBreakdown(invoice), 7.5f, false, INK, false);
        }

        rows.add(ReceiptRow.spacer(3));
        rows.add(ReceiptRow.rule());
        rows.add(ReceiptRow.pair("ITEM  QTY X RATE", "AMOUNT", 7.5f, true));
        rows.add(ReceiptRow.rule());
        for (SaleItem item : invoice.getItems()) {
            addWrapped(rows, item.getProductName(), 8, true, INK, false);
            String quantityAndRate = item.getQuantity() + " x " + money(item.getUnitPrice());
            if (invoice.isGstInvoice())
                quantityAndRate += "  GST " + percent(item.getGstRate());
            BigDecimal lineAmount = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            rows.add(ReceiptRow.pair(quantityAndRate, money(lineAmount), 7.5f, false));
            rows.add(ReceiptRow.rule());
        }

        rows.add(ReceiptRow.pair("Subtotal", money(invoice.getTaxableAmount().add(invoice.getDiscountAmount())), 8,
                false));
        if (invoice.getDiscountAmount().signum() > 0) {
            rows.add(ReceiptRow.pair("Discount", "-" + money(invoice.getDiscountAmount()), 8, false));
        }
        if (invoice.isGstInvoice()) {
            rows.add(ReceiptRow.pair("Taxable amount", money(invoice.getTaxableAmount()), 8, false));
        }
        if (invoice.getCgstAmount().signum() > 0)
            rows.add(ReceiptRow.pair("CGST", money(invoice.getCgstAmount()), 8, false));
        if (invoice.getSgstAmount().signum() > 0)
            rows.add(ReceiptRow.pair("SGST", money(invoice.getSgstAmount()), 8, false));
        if (invoice.getIgstAmount().signum() > 0)
            rows.add(ReceiptRow.pair("IGST", money(invoice.getIgstAmount()), 8, false));
        rows.add(ReceiptRow.rule());
        rows.add(ReceiptRow.pair("GRAND TOTAL", money(invoice.getTotalAmount()), 10, true));
        rows.add(ReceiptRow.rule());
        rows.add(ReceiptRow.spacer(3));
        rows.add(ReceiptRow.center("Thank you for shopping with us!", 8, true, INK, LINE_HEIGHT));
        addWrapped(rows, businessFooter(business, invoice), 7, false, MUTED, true);

        float contentHeight = rows.stream().map(ReceiptRow::height).reduce(0f, Float::sum);
        float pageHeight = Math.max(320, contentHeight + 28);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, pageHeight));
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = pageHeight - 14;
                for (ReceiptRow row : rows) {
                    y = row.draw(stream, y);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    // CHANGED: added "throws IOException" (calls ReceiptRow.pair)
    private void addOptionalPair(List<ReceiptRow> rows, String label, String value) throws IOException {
        if (value != null && !value.isBlank())
            rows.add(ReceiptRow.pair(label, value.trim(), 7.5f, false));
    }

    // CHANGED: added "throws IOException" (calls ReceiptRow.wrapped)
    private void addWrapped(List<ReceiptRow> rows, String value, float size, boolean bold, Color color,
            boolean centered) throws IOException {
        if (value == null || value.isBlank())
            return;
        rows.addAll(ReceiptRow.wrapped(value, size, bold, color, CONTENT_WIDTH, centered));
    }

    private String paymentBreakdown(Invoice invoice) {
        return Stream.of(
                tender("Cash", invoice.getCashPaymentAmount()),
                tender("UPI", invoice.getUpiPaymentAmount()),
                tender("Credit Card", invoice.getCreditCardPaymentAmount()),
                tender("Debit Card", invoice.getDebitCardPaymentAmount()))
                .filter(value -> !value.isBlank()).reduce((first, second) -> first + ", " + second).orElse("-");
    }

    private String tender(String mode, BigDecimal amount) {
        return amount == null || amount.signum() <= 0 ? "" : mode + " " + money(amount);
    }

    private String businessFooter(BusinessProfile business, Invoice invoice) {
        String gstin = invoice.isGstInvoice() ? invoice.getSellerGstin() : business.getGstin();
        return Stream.of(address(business), business.getOwnerContact(), business.getOwnerEmail(), gstin)
                .filter(value -> value != null && !value.isBlank()).map(String::trim)
                .reduce((first, second) -> first + " | " + second).orElse("");
    }

    private String address(BusinessProfile business) {
        return Stream.of(business.getShopAddress(), business.getCity(), business.getState(), business.getPostalCode())
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct()
                .reduce((first, second) -> first + ", " + second).orElse("");
    }

    private String initials(String shop) {
        return Stream.of(shop.trim().split("\\s+")).limit(2).map(part -> part.substring(0, 1))
                .reduce("", String::concat).toUpperCase(Locale.ROOT);
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String percent(BigDecimal value) {
        return NumberFormat.getNumberInstance(Locale.ENGLISH).format(value == null ? BigDecimal.ZERO : value) + "%";
    }

    private String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "Rs. " + format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static class ReceiptRow {
        private final List<String> leftLines;
        private final String right;
        private final float size;
        private final boolean bold;
        private final Color color;
        private final boolean centered;
        private final boolean rule;
        private final float fixedHeight;

        private ReceiptRow(List<String> leftLines, String right, float size, boolean bold, Color color,
                boolean centered, boolean rule, float fixedHeight) {
            this.leftLines = leftLines;
            this.right = right;
            this.size = size;
            this.bold = bold;
            this.color = color;
            this.centered = centered;
            this.rule = rule;
            this.fixedHeight = fixedHeight;
        }

        static ReceiptRow center(String text, float size, boolean bold, Color color, float height) {
            return new ReceiptRow(List.of(safe(text)), "", size, bold, color, true, false, height);
        }

        // CHANGED: added "throws IOException" (calls wrap)
        static ReceiptRow pair(String left, String right, float size, boolean bold) throws IOException {
            float leftWidth = CONTENT_WIDTH - 91;
            return new ReceiptRow(wrap(left, leftWidth, size, bold), safe(right), size, bold, INK, false, false, 0);
        }

        static ReceiptRow rule() {
            return new ReceiptRow(List.of(), "", 0, false, RULE, false, true, 5);
        }

        static ReceiptRow spacer(float height) {
            return new ReceiptRow(List.of(), "", 0, false, INK, false, false, height);
        }

        // CHANGED: added "throws IOException" (calls wrap)
        static List<ReceiptRow> wrapped(String text, float size, boolean bold, Color color, float width,
                boolean centered) throws IOException {
            List<ReceiptRow> result = new ArrayList<>();
            for (String line : wrap(text, width, size, bold)) {
                result.add(new ReceiptRow(List.of(line), "", size, bold, color, centered, false, 0));
            }
            return result;
        }

        float height() {
            if (fixedHeight > 0)
                return fixedHeight;
            return Math.max(1, leftLines.size()) * LINE_HEIGHT;
        }

        float draw(PDPageContentStream stream, float top) throws IOException {
            if (rule) {
                stream.setStrokingColor(color);
                stream.setLineWidth(.5f);
                stream.moveTo(SIDE_MARGIN, top - fixedHeight / 2);
                stream.lineTo(PAGE_WIDTH - SIDE_MARGIN, top - fixedHeight / 2);
                stream.stroke();
                return top - fixedHeight;
            }
            if (leftLines.isEmpty())
                return top - fixedHeight;
            PDType1Font font = font(bold);
            for (int index = 0; index < leftLines.size(); index++) {
                String line = leftLines.get(index);
                float baseline = top - 8 - index * LINE_HEIGHT;
                stream.beginText();
                stream.setNonStrokingColor(color);
                stream.setFont(font, size);
                float x = SIDE_MARGIN;
                if (centered)
                    x = (PAGE_WIDTH - font.getStringWidth(line) / 1000 * size) / 2;
                stream.newLineAtOffset(x, baseline);
                stream.showText(line);
                stream.endText();
                if (index == 0 && !right.isBlank()) {
                    String fittedRight = fit(right, font, size, 86);
                    float rightX = PAGE_WIDTH - SIDE_MARGIN - font.getStringWidth(fittedRight) / 1000 * size;
                    stream.beginText();
                    stream.setNonStrokingColor(color);
                    stream.setFont(font, size);
                    stream.newLineAtOffset(rightX, baseline);
                    stream.showText(fittedRight);
                    stream.endText();
                }
            }
            return top - height();
        }

        // CHANGED: added "throws IOException" (font.getStringWidth can throw)
        private static List<String> wrap(String value, float maxWidth, float size, boolean bold) throws IOException {
            PDType1Font font = font(bold);
            String safe = safe(value == null ? "" : value.trim());
            if (safe.isBlank())
                return List.of("");
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : safe.split("\\s+")) {
                if (font.getStringWidth(word) / 1000 * size > maxWidth) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    StringBuilder chunk = new StringBuilder();
                    for (char character : word.toCharArray()) {
                        String candidate = chunk.toString() + character;
                        if (!chunk.isEmpty() && font.getStringWidth(candidate) / 1000 * size > maxWidth) {
                            lines.add(chunk.toString());
                            chunk.setLength(0);
                        }
                        chunk.append(character);
                    }
                    if (!chunk.isEmpty())
                        lines.add(chunk.toString());
                    continue;
                }
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.getStringWidth(next) / 1000 * size > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(next);
                }
            }
            if (!line.isEmpty())
                lines.add(line.toString());
            return lines;
        }

        private static String fit(String value, PDType1Font font, float size, float maxWidth) throws IOException {
            String text = safe(value);
            if (font.getStringWidth(text) / 1000 * size <= maxWidth)
                return text;
            while (!text.isEmpty() && font.getStringWidth(text + "...") / 1000 * size > maxWidth) {
                text = text.substring(0, text.length() - 1);
            }
            return text + "...";
        }

        private static PDType1Font font(boolean bold) {
            return bold ? PDType1Font.COURIER_BOLD : PDType1Font.COURIER;
        }

        private static String safe(String value) {
            if (value == null)
                return "";
            StringBuilder result = new StringBuilder();
            value.codePoints().forEach(codePoint -> result.append(codePoint >= 32 && codePoint <= 126
                    ? (char) codePoint
                    : ' '));
            return result.toString();
        }
    }
}