package com.smartretail.billing.service;

import com.smartretail.billing.entity.BusinessProfile;
import com.smartretail.billing.entity.Invoice;
import com.smartretail.billing.entity.SaleItem;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class InvoiceEmailService {
    private static final DateTimeFormatter INVOICE_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final InvoicePdfService invoicePdfService;

    @Autowired
    public InvoiceEmailService(JavaMailSender mailSender,
                               @Value("${smart-retail.email.from:}") String fromAddress,
                               InvoicePdfService invoicePdfService) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.invoicePdfService = invoicePdfService;
    }

    public InvoiceEmailService(JavaMailSender mailSender, String fromAddress) {
        this(mailSender, fromAddress, new InvoicePdfService());
    }

    public void sendInvoice(String recipient, BusinessProfile business, Invoice invoice,
                            String customerPhone, String customerAddress) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        String shopName = business.getShopName() == null || business.getShopName().isBlank()
                ? "Smart Retail" : business.getShopName().trim();
        String brandInitials = java.util.Arrays.stream(shopName.split("\\s+"))
                .limit(2).map(part -> part.substring(0, 1)).collect(Collectors.joining()).toUpperCase(Locale.ROOT);
        helper.setTo(recipient);
        if (fromAddress != null && !fromAddress.isBlank()) helper.setFrom(fromAddress);
        helper.setSubject("Your " + (invoice.isGstInvoice() ? "GST invoice" : "receipt")
                + " from " + shopName + " - " + invoice.getInvoiceNumber());
        helper.setText(plainText(business, invoice, customerPhone, customerAddress, recipient),
                html(business, invoice, customerPhone, customerAddress, recipient));
        helper.addAttachment(invoice.getInvoiceNumber() + ".pdf",
                () -> new java.io.ByteArrayInputStream(invoicePdfService.create(
                        business, invoice, customerPhone, customerAddress, recipient)),
                "application/pdf");
        mailSender.send(message);
    }

    public void sendPaymentReminder(String recipient, BusinessProfile business, Invoice invoice)
            throws MessagingException {
        sendPaymentReminder(recipient, business, invoice, null, null);
    }

    public void sendPaymentReminder(String recipient, BusinessProfile business, Invoice invoice,
                                    String customerPhone, String customerAddress) throws MessagingException {
        String shopName = business.getShopName() == null || business.getShopName().isBlank()
                ? "Smart Retail" : business.getShopName().trim();
        String dueAt = invoice.getPaymentDueAt() == null ? "" : invoice.getPaymentDueAt()
                .atZone(ZoneId.of("Asia/Kolkata")).format(INVOICE_DATE);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(recipient);
        if (fromAddress != null && !fromAddress.isBlank()) helper.setFrom(fromAddress);
        helper.setSubject("Payment reminder from " + shopName + " - " + invoice.getInvoiceNumber());
        helper.setText(paymentReminderText(business, invoice, dueAt),
                paymentReminderHtml(business, invoice, customerPhone, customerAddress, recipient, dueAt));
        helper.addAttachment(invoice.getInvoiceNumber() + ".pdf",
                () -> new java.io.ByteArrayInputStream(invoicePdfService.create(
                        business, invoice, customerPhone, customerAddress, recipient)),
                "application/pdf");
        mailSender.send(message);
    }

    private String paymentReminderText(BusinessProfile business, Invoice invoice, String dueAt) {
        String shopName = business.getShopName() == null || business.getShopName().isBlank()
                ? "Smart Retail" : business.getShopName().trim();
        return "Hello " + (invoice.getBuyerName() == null || invoice.getBuyerName().isBlank()
                ? "there" : invoice.getBuyerName().trim()) + ",\n\n"
                + "This is a friendly reminder that invoice " + invoice.getInvoiceNumber() + " has an outstanding balance.\n"
                + "Invoice total: " + money(invoice.getTotalAmount()) + "\n"
                + "Amount paid: " + money(invoice.getPaidAmount()) + "\n"
                + "Balance due: " + money(invoice.getBalanceDue()) + "\n"
                + (dueAt.isBlank() ? "" : "Due date (IST): " + dueAt + "\n")
                + "The current invoice PDF is attached for your records. If you have already paid, please contact "
                + shopName + " so we can update the payment status.\n\nThank you.";
    }

    private String paymentReminderHtml(BusinessProfile business, Invoice invoice, String customerPhone,
                                       String customerAddress, String customerEmail, String dueAt) {
        String shopName = business.getShopName() == null || business.getShopName().isBlank()
                ? "Smart Retail" : business.getShopName().trim();
        String customerName = invoice.getBuyerName() == null || invoice.getBuyerName().isBlank()
                ? "there" : invoice.getBuyerName().trim();
        String dueRow = dueAt.isBlank() ? "" : detailRow("Due date (IST)", dueAt);
        return "<!doctype html><html><body style='margin:0;padding:24px;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#172033;'>"
                + "<table role='presentation' style='width:100%;max-width:640px;margin:auto;border:1px solid #dbe4ee;border-radius:14px;background:#fff;border-collapse:separate;border-spacing:0;overflow:hidden;'>"
                + "<tr><td style='padding:22px 26px;background:#0f2742;color:#fff;'><div style='font-size:11px;letter-spacing:1px;color:#b9d5f2;'>PAYMENT REMINDER</div>"
                + "<div style='font-size:21px;font-weight:700;margin-top:7px;'>" + escape(shopName) + "</div></td></tr>"
                + "<tr><td style='padding:24px 26px;'><p style='margin:0 0 16px;font-size:14px;line-height:1.6;'>Hello "
                + escape(customerName) + ", this is a friendly reminder that invoice <strong>" + escape(invoice.getInvoiceNumber())
                + "</strong> has an outstanding balance. The updated invoice PDF is attached for your records.</p>"
                + "<table role='presentation' style='width:100%;border-collapse:collapse;background:#f8fafc;font-size:13px;'>"
                + detailRow("Invoice", invoice.getInvoiceNumber())
                + detailRow("Invoice total", money(invoice.getTotalAmount()))
                + detailRow("Amount paid", money(invoice.getPaidAmount()))
                + "<tr><td style='padding:11px 10px;border-top:1px solid #dbe4ee;color:#334155;font-weight:700;'>Balance due</td>"
                + "<td style='padding:11px 10px;border-top:1px solid #dbe4ee;text-align:right;color:#b45309;font-size:17px;font-weight:700;'>"
                + money(invoice.getBalanceDue()) + "</td></tr>" + dueRow + "</table>"
                + "<p style='margin:18px 0 0;color:#475569;font-size:13px;line-height:1.6;'>If you have already paid, please reply to this email or contact "
                + escape(shopName) + " so the payment can be updated. The attached PDF shows the invoice and current recorded payment details.</p>"
                + "<p style='margin:18px 0 0;color:#64748b;font-size:12px;'>" + escape(customerEmail)
                + (customerPhone == null || customerPhone.isBlank() ? "" : " · " + escape(customerPhone))
                + (customerAddress == null || customerAddress.isBlank() ? "" : "<br>" + escape(customerAddress))
                + "</p>" + businessFooter(business, invoice) + "</td></tr></table></body></html>";
    }

    private String html(BusinessProfile business, Invoice invoice, String customerPhone,
                        String customerAddress, String customerEmail) {
        String shopName = business.getShopName() == null || business.getShopName().isBlank()
                ? "Smart Retail" : business.getShopName().trim();
        String brandInitials = java.util.Arrays.stream(shopName.split("\\s+"))
                .limit(2).map(part -> part.substring(0, 1)).collect(Collectors.joining()).toUpperCase(Locale.ROOT);
        StringBuilder itemRows = new StringBuilder();
        for (SaleItem item : invoice.getItems()) {
            BigDecimal lineTotal = item.getTaxableAmount().add(item.getGstAmount()).add(item.getBillDiscount());
            itemRows.append("<tr>")
                    .append(cell(escape(item.getProductName()), "text-align:left;"))
                    .append(cell(String.valueOf(item.getQuantity()), "text-align:center;"))
                    .append(cell(money(item.getUnitPrice()), "text-align:right;"))
                    .append(cell(money(item.getGstRate()) + "%", "text-align:right;"))
                    .append(cell(money(lineTotal), "text-align:right;font-weight:700;"))
                    .append("</tr>");
        }

        String detailsRows = detailRow("Invoice", invoice.getInvoiceNumber())
                + detailRow("Date & time (IST)", invoice.getSoldAt().format(INVOICE_DATE))
                + detailRow("Customer", invoice.getBuyerName() == null ? "Walk-in customer" : invoice.getBuyerName())
                + detailRow("Phone", customerPhone)
                + detailRow("Email", customerEmail)
                + detailRow("Customer address", customerAddress);
        if (invoice.getPaymentDueAt() != null) {
            detailsRows += detailRow("Payment due (IST)", invoice.getPaymentDueAt()
                    .atZone(ZoneId.of("Asia/Kolkata")).format(INVOICE_DATE));
        }
        if (invoice.isGstInvoice()) {
            detailsRows += detailRow("Seller GSTIN", invoice.getSellerGstin());
            detailsRows += detailRow("Buyer GSTIN", invoice.getBuyerGstin());
        }

        String gstBreakdown = invoice.isGstInvoice()
                ? "<tr><td colspan='2' style='padding:14px 0 4px;border-top:1px solid #e5eeeb;color:#738782;font-size:11px;font-weight:700;'>GST breakdown (included)</td></tr>"
                + summaryRow("Taxable amount", invoice.getTaxableAmount())
                    + summaryRow("CGST", invoice.getCgstAmount())
                    + summaryRow("SGST", invoice.getSgstAmount())
                    + summaryRow("IGST", invoice.getIgstAmount())
                : "";

        return "<!doctype html><html><body style='margin:0;padding:20px;background:#f2f6f5;font-family:Arial,Helvetica,sans-serif;color:#172a2a;'>"
                + "<table role='presentation' style='width:100%;max-width:680px;margin:0 auto;background:#fff;border:1px solid #dce6e3;border-radius:12px;border-collapse:separate;border-spacing:0;overflow:hidden;'>"
                + "<tr><td style='padding:24px 28px;background:#12312e;color:#fff;'>"
                + "<span style='display:inline-block;width:38px;height:38px;line-height:38px;text-align:center;border-radius:11px;background:#0f766e;color:#fff;font-size:13px;font-weight:bold;vertical-align:middle;margin-right:12px;'>" + escape(brandInitials) + "</span>"
                + "<div style='font-size:11px;letter-spacing:1.2px;color:#b8ddd7;'>" + (invoice.isGstInvoice() ? "GST TAX INVOICE" : "RETAIL RECEIPT") + "</div>"
                + "<div style='margin-top:8px;font-size:23px;font-weight:700;'>" + escape(shopName) + "</div>"
                + "<div style='margin-top:5px;color:#d4e4e1;font-size:13px;'>" + escape(businessAddressText(business)) + "</div>"
                + "</td></tr><tr><td style='padding:24px 28px;'>"
                + "<p style='margin:0 0 18px;color:#425b57;font-size:14px;line-height:1.6;'>Hello "
                + escape(invoice.getBuyerName() == null || invoice.getBuyerName().isBlank() ? "there" : invoice.getBuyerName())
                + ", thank you for shopping with us. Your complete bill is included below, with a printable PDF copy attached.</p>"
                + "<table role='presentation' style='width:100%;border-collapse:collapse;background:#f6faf9;font-size:13px;'>"
                + detailsRows + "</table>"
                + "<table style='width:100%;margin-top:20px;border-collapse:collapse;font-size:13px;'>"
                + "<thead><tr style='background:#e7f4f2;color:#315c56;'>"
                + headerCell("Item", "text-align:left;") + headerCell("Qty", "text-align:center;")
                + headerCell("Rate", "text-align:right;") + headerCell("GST", "text-align:right;")
                + headerCell("Amount", "text-align:right;") + "</tr></thead><tbody>" + itemRows + "</tbody></table>"
                + "<table role='presentation' style='width:100%;max-width:340px;margin:18px 0 0 auto;border-collapse:collapse;font-size:13px;'>"
                + summaryRow("Subtotal", invoice.getTaxableAmount().add(invoice.getDiscountAmount()))
                + (invoice.getDiscountAmount().signum() > 0 ? summaryRow("Discount", invoice.getDiscountAmount().negate()) : "")
                + gstBreakdown
                + "<tr><td style='padding:13px 0 4px;border-top:2px solid #0f766e;color:#0f625d;font-size:17px;font-weight:700;'>Amount paid</td>"
                + "<td style='padding:13px 0 4px;border-top:2px solid #0f766e;color:#0f625d;text-align:right;font-size:17px;font-weight:700;'>" + money(invoice.getPaidAmount()) + "</td></tr>"
                + (invoice.getBalanceDue().signum() > 0 ? "<tr><td style='padding:5px 0;color:#536a65;'>Balance due</td><td style='padding:5px 0;text-align:right;color:#9a5800;font-weight:700;'>" + money(invoice.getBalanceDue()) + "</td></tr>" : "")
                + "</table><p style='margin:24px 0 0;color:#425b57;font-size:13px;'>Thank you for choosing us. We hope to see you again.</p>"
                + businessFooter(business, invoice)
                + "</td></tr></table></body></html>";
    }

    private String plainText(BusinessProfile business, Invoice invoice, String customerPhone,
                             String customerAddress, String customerEmail) {
        String shopName = business.getShopName() == null || business.getShopName().isBlank()
                ? "Smart Retail" : business.getShopName();
        StringBuilder text = new StringBuilder("Hello ")
                .append(invoice.getBuyerName() == null || invoice.getBuyerName().isBlank() ? "there" : invoice.getBuyerName())
                .append(", thank you for shopping with ").append(shopName).append(".\n\n")
                .append(invoice.isGstInvoice() ? "GST Tax Invoice" : "Retail Receipt").append(" ")
                .append(invoice.getInvoiceNumber()).append("\n")
                .append("Date & time (IST): ").append(invoice.getSoldAt().format(INVOICE_DATE)).append("\n");
        appendContact(text, "Phone", customerPhone);
        appendContact(text, "Email", customerEmail);
        appendContact(text, "Customer address", customerAddress);
        text.append("Subtotal: ").append(money(invoice.getTaxableAmount().add(invoice.getDiscountAmount()))).append("\n");
        if (invoice.getDiscountAmount().signum() > 0) {
            text.append("Discount: -").append(money(invoice.getDiscountAmount())).append("\n");
        }
        if (invoice.isGstInvoice()) {
            text.append("Seller GSTIN: ").append(invoice.getSellerGstin()).append("\n");
            appendContact(text, "Buyer GSTIN", invoice.getBuyerGstin());
        }
        text.append("\nItems\n");
        for (SaleItem item : invoice.getItems()) {
            BigDecimal lineTotal = item.getTaxableAmount().add(item.getGstAmount()).add(item.getBillDiscount());
            text.append(item.getProductName()).append(" x ").append(item.getQuantity()).append(" @ ")
                    .append(money(item.getUnitPrice())).append(" | GST ").append(money(item.getGstRate()))
                    .append("% | ").append(money(lineTotal)).append("\n");
        }
        if (invoice.isGstInvoice()) {
            text.append("\nGST breakdown (included)\n")
                    .append("Taxable amount: ").append(money(invoice.getTaxableAmount())).append("\n")
                    .append("CGST: ").append(money(invoice.getCgstAmount())).append("\n")
                    .append("SGST: ").append(money(invoice.getSgstAmount())).append("\n")
                    .append("IGST: ").append(money(invoice.getIgstAmount())).append("\n");
        }
        text.append("\nAmount paid: ").append(money(invoice.getPaidAmount()))
                .append("\nBalance due: ").append(money(invoice.getBalanceDue()))
                .append(invoice.getPaymentDueAt() == null ? "" : "\nPayment due (IST): " + invoice.getPaymentDueAt().atZone(ZoneId.of("Asia/Kolkata")).format(INVOICE_DATE))
                .append("\n\nThank you for choosing us. We hope to see you again.\n\n")
                .append("A printable PDF copy of this bill is attached.\n\n")
                .append("BUSINESS DETAILS\n").append(shopName).append("\n");
        appendContact(text, "Address", businessAddressText(business));
        appendContact(text, "Phone", business.getOwnerContact());
        appendContact(text, "Email", business.getOwnerEmail());
        String gstin = invoice.isGstInvoice() ? invoice.getSellerGstin() : business.getGstin();
        appendContact(text, "GSTIN", gstin);
        return text.toString();
    }

    private String businessFooter(BusinessProfile business, Invoice invoice) {
        String shopName = business.getShopName() == null || business.getShopName().isBlank()
                ? "Smart Retail" : business.getShopName();
        String gstin = invoice.isGstInvoice() ? invoice.getSellerGstin() : business.getGstin();
        return "<table role='presentation' style='width:100%;margin-top:20px;border-top:1px solid #e5eeeb;border-collapse:collapse;color:#667875;font-size:12px;line-height:1.6;'>"
                + "<tr><td style='padding-top:14px;'><strong style='color:#233b37;'>Business details</strong><br>"
                + escape(shopName) + optionalBusinessLine(businessAddressText(business))
                + optionalBusinessLine(prefixed("Phone", business.getOwnerContact()))
                + optionalBusinessLine(prefixed("Email", business.getOwnerEmail()))
                + optionalBusinessLine(prefixed("GSTIN", gstin))
                + "</td></tr></table>";
    }

    private String businessAddressText(BusinessProfile business) {
        return Stream.of(business.getShopAddress(), business.getCity(), business.getState(), business.getPostalCode())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String detailRow(String label, String value) {
        if (value == null || value.isBlank()) return "";
        return "<tr><td style='padding:7px 10px;color:#738782;width:34%;'>" + escape(label)
                + "</td><td style='padding:7px 10px;color:#233b37;font-weight:600;'>" + escape(value) + "</td></tr>";
    }

    private String cell(String value, String style) {
        return "<td style='padding:11px 9px;border-bottom:1px solid #e5eeeb;" + style + "'>" + value + "</td>";
    }

    private String headerCell(String value, String style) {
        return "<th style='padding:10px 9px;" + style + "'>" + value + "</th>";
    }

    private String summaryRow(String label, BigDecimal value) {
        if (value == null || value.signum() == 0) return "";
        return "<tr><td style='padding:5px 0;color:#536a65;'>" + escape(label) + "</td><td style='padding:5px 0;text-align:right;color:#233b37;'>" + money(value) + "</td></tr>";
    }

    private String optionalBusinessLine(String value) {
        return value == null || value.isBlank() ? "" : "<br>" + escape(value);
    }

    private String prefixed(String label, String value) {
        return value == null || value.isBlank() ? "" : label + ": " + value.trim();
    }

    private void appendContact(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) text.append(label).append(": ").append(value).append("\n");
    }

    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(new Locale("en", "IN"))
                .format(value == null ? BigDecimal.ZERO : value);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
