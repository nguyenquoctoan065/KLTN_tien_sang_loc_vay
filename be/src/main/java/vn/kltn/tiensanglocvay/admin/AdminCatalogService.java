package vn.kltn.tiensanglocvay.admin;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.admin.AdminDtos.AdminCatalog;
import vn.kltn.tiensanglocvay.admin.AdminDtos.CrossSellProductInput;
import vn.kltn.tiensanglocvay.admin.AdminDtos.DocumentTypeDto;
import vn.kltn.tiensanglocvay.admin.AdminDtos.DocumentTypeInput;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ProductDocRule;
import vn.kltn.tiensanglocvay.admin.AdminDtos.UpdateProductRequest;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.catalog.CrossSellProduct;
import vn.kltn.tiensanglocvay.catalog.CrossSellProductDto;
import vn.kltn.tiensanglocvay.catalog.CrossSellProductRepository;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.catalog.DocumentTypeRepository;
import vn.kltn.tiensanglocvay.catalog.LoanProduct;
import vn.kltn.tiensanglocvay.catalog.LoanProductDto;
import vn.kltn.tiensanglocvay.catalog.LoanProductRepository;
import vn.kltn.tiensanglocvay.catalog.ProductDocumentType;
import vn.kltn.tiensanglocvay.catalog.ProductDocumentTypeRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * 7.3 Danh mục (gói vay, loại giấy tờ, giấy tờ theo gói, bán chéo), chép từ fe/src/mocks/handlers/admin.ts. Trả mọi bản ghi kể cả
 * INACTIVE; tên loại giấy tờ là tên gốc (không nối "– hệ thống tự lập" như checklist công khai). Giấy tờ theo gói áp dụng cho hồ sơ
 * tạo sau khi cập nhật (hồ sơ cũ đã có danh mục giấy tờ riêng).
 */
@Service
public class AdminCatalogService {

    static final String MSG_PRODUCT_NOT_FOUND = "Không tìm thấy gói vay.";
    static final String MSG_DOC_TYPE_NOT_FOUND = "Không tìm thấy loại giấy tờ.";
    static final String MSG_SYSTEM_DOC_INACTIVE = "Không ngừng áp dụng giấy đề nghị vay vốn.";
    static final String MSG_NEED_SYSTEM_DOC = "Mỗi gói phải có giấy đề nghị vay vốn.";
    static final String MSG_CROSS_SELL_NOT_FOUND = "Không tìm thấy sản phẩm.";

    private static final Sort BY_ID = Sort.by("id");
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999999");
    private static final BigDecimal MAX_DEC52 = new BigDecimal("999.99");

    private final LoanProductRepository products;
    private final DocumentTypeRepository docTypes;
    private final ProductDocumentTypeRepository rules;
    private final CrossSellProductRepository crossSells;
    private final ActivityLogService activity;

    public AdminCatalogService(LoanProductRepository products, DocumentTypeRepository docTypes, ProductDocumentTypeRepository rules,
                               CrossSellProductRepository crossSells, ActivityLogService activity) {
        this.products = products;
        this.docTypes = docTypes;
        this.rules = rules;
        this.crossSells = crossSells;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public AdminCatalog catalog() {
        return new AdminCatalog(products.findAll(BY_ID).stream().map(LoanProductDto::of).toList(),
                docTypes.findAll(BY_ID).stream().map(AdminCatalogService::docType).toList(), currentRules(),
                crossSells.findAll(BY_ID).stream().map(CrossSellProductDto::of).toList());
    }

    @Transactional
    public LoanProductDto updateProduct(int id, UpdateProductRequest b, CurrentUser u) {
        LoanProduct p = products.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_PRODUCT_NOT_FOUND));
        UpdateProductRequest r = b == null ? new UpdateProductRequest(null, null, null, null) : b;
        FieldErrors e = new FieldErrors();
        if (r.name() != null) {
            e.text("name", r.name(), 150);
        }
        if (r.repaymentMethod() != null) {
            e.text("repaymentMethod", r.repaymentMethod(), 50);
        }
        if (r.status() != null) {
            e.oneOf("status", r.status(), FieldErrors.STATUSES);
        }
        e.throwIfAny();
        p.update(FieldErrors.trim(r.name()), r.description(), FieldErrors.trim(r.repaymentMethod()), r.status());
        activity.log(u, "Cập nhật gói vay", p.getName(), null);
        return LoanProductDto.of(p);
    }

    @Transactional
    public DocumentTypeDto createDocType(DocumentTypeInput b, CurrentUser u) {
        validate(b);
        DocumentType t = docTypes.save(new DocumentType(b.name().trim(), b.group().trim(), formats(b.allowedFormats()), b.validityDays(),
                b.status()));
        activity.log(u, "Thêm loại giấy tờ", t.getName(), null);
        return docType(t);
    }

    @Transactional
    public DocumentTypeDto updateDocType(int id, DocumentTypeInput b, CurrentUser u) {
        DocumentType t = docTypes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_DOC_TYPE_NOT_FOUND));
        if (t.getId() == DocumentType.SYSTEM_DOC_TYPE && b != null && "INACTIVE".equals(b.status())) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_SYSTEM_DOC_INACTIVE);
        }
        validate(b);
        t.update(b.name().trim(), b.group().trim(), formats(b.allowedFormats()), b.validityDays(), b.status());
        activity.log(u, "Cập nhật loại giấy tờ", t.getName(), null);
        return docType(t);
    }

    @Transactional
    public List<ProductDocRule> replaceRules(List<ProductDocRule> b, CurrentUser u) {
        if (b == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FieldErrors.INVALID);
        }
        for (String pc : List.of("TD", "TC")) {
            if (b.stream().noneMatch(r -> r != null && pc.equals(r.productCode()) && r.documentTypeId() != null
                    && r.documentTypeId() == DocumentType.SYSTEM_DOC_TYPE)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, MSG_NEED_SYSTEM_DOC);
            }
        }
        Map<String, Integer> productIds = products.findAll().stream().collect(Collectors.toMap(LoanProduct::getCode, LoanProduct::getId));
        Set<Integer> typeIds = docTypes.findAll().stream().map(DocumentType::getId).collect(Collectors.toSet());
        FieldErrors e = new FieldErrors();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < b.size(); i++) {
            ProductDocRule r = b.get(i);
            if (r == null || !productIds.containsKey(r.productCode()) || r.documentTypeId() == null
                    || !typeIds.contains(r.documentTypeId().intValue()) || r.required() == null) {
                e.add("[" + i + "]", "Cần mã gói, loại giấy tờ có trong danh mục và cờ bắt buộc.");
            } else if (!seen.add(r.productCode() + "|" + r.documentTypeId())) {
                e.add("[" + i + "]", "Loại giấy tờ bị lặp trong cùng gói.");
            }
        }
        e.throwIfAny();
        rules.deleteAllInBatch();
        rules.saveAll(b.stream().map(r -> new ProductDocumentType(productIds.get(r.productCode()), r.documentTypeId().intValue(),
                r.required())).toList());
        rules.flush();
        activity.log(u, "Cập nhật giấy tờ theo gói vay", b.size() + " dòng", null);
        return currentRules();
    }

    @Transactional
    public CrossSellProductDto createCrossSell(CrossSellProductInput b, CurrentUser u) {
        validate(b);
        CrossSellProduct c = crossSells.save(new CrossSellProduct(b.name().trim(), b.productType().trim(), b.estCost(), b.scoreBonus(),
                b.rateDiscount(), b.status()));
        activity.log(u, "Thêm sản phẩm bán chéo", c.getName(), null);
        return CrossSellProductDto.of(c);
    }

    @Transactional
    public CrossSellProductDto updateCrossSell(int id, CrossSellProductInput b, CurrentUser u) {
        CrossSellProduct c = crossSells.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_CROSS_SELL_NOT_FOUND));
        validate(b);
        c.update(b.name().trim(), b.productType().trim(), b.estCost(), b.scoreBonus(), b.rateDiscount(), b.status());
        activity.log(u, "Cập nhật sản phẩm bán chéo", c.getName(), null);
        return CrossSellProductDto.of(c);
    }

    // ---------------------------------------------------------------- tiện ích

    private List<ProductDocRule> currentRules() {
        Map<Integer, String> codes = products.findAll().stream().collect(Collectors.toMap(LoanProduct::getId, LoanProduct::getCode));
        return rules.findAllOrdered().stream()
                .map(r -> new ProductDocRule(codes.get(r.getProductId()), r.getDocumentTypeId().longValue(), r.isRequired())).toList();
    }

    private static DocumentTypeDto docType(DocumentType t) {
        return new DocumentTypeDto(t.getId(), t.getName(), t.getGroup(), t.getAllowedFormats(), t.getValidityDays(), t.getStatus());
    }

    /** "jpg, png" → "JPG,PNG" (QualityChecker so khớp đuôi tệp theo danh sách này). */
    private static String formats(String raw) {
        return String.join(",", Arrays.stream(raw.split(",")).map(s -> s.trim().toUpperCase()).filter(s -> !s.isEmpty()).toList());
    }

    private static void validate(DocumentTypeInput b) {
        FieldErrors e = new FieldErrors();
        if (b == null) {
            e.add("name", "Không được để trống.");
        } else {
            e.text("name", b.name(), 150);
            e.text("group", b.group(), 30);
            e.text("allowedFormats", b.allowedFormats(), 50);
            if (b.allowedFormats() != null && !b.allowedFormats().isBlank() && formats(b.allowedFormats()).isEmpty()) {
                e.add("allowedFormats", "Cần ít nhất một định dạng.");
            }
            if (b.validityDays() != null && b.validityDays() < 1) {
                e.add("validityDays", "Phải lớn hơn 0 hoặc để trống.");
            }
            e.oneOf("status", b.status(), FieldErrors.STATUSES);
        }
        e.throwIfAny();
    }

    private static void validate(CrossSellProductInput b) {
        FieldErrors e = new FieldErrors();
        if (b == null) {
            e.add("name", "Không được để trống.");
        } else {
            e.text("name", b.name(), 150);
            e.text("productType", b.productType(), 50);
            e.decimal("estCost", b.estCost(), BigDecimal.ZERO, MAX_MONEY, 0);
            e.decimal("scoreBonus", b.scoreBonus(), BigDecimal.ZERO, MAX_DEC52, 2);
            e.decimal("rateDiscount", b.rateDiscount(), BigDecimal.ZERO, MAX_DEC52, 2);
            e.oneOf("status", b.status(), FieldErrors.STATUSES);
        }
        e.throwIfAny();
    }
}
