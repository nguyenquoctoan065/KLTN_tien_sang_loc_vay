package vn.kltn.tiensanglocvay.catalog;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Danh mục công khai; chỉ trả bản ghi ACTIVE như fe/src/mocks/handlers/catalog.ts. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final String ACTIVE = "ACTIVE";

    private final LoanProductRepository products;
    private final CrossSellProductRepository crossSells;
    private final BranchRepository branches;
    private final ProductDocumentTypeRepository productDocs;

    public CatalogService(LoanProductRepository products, CrossSellProductRepository crossSells, BranchRepository branches,
                          ProductDocumentTypeRepository productDocs) {
        this.products = products;
        this.crossSells = crossSells;
        this.branches = branches;
        this.productDocs = productDocs;
    }

    public List<LoanProductDto> activeProducts() {
        return products.findByStatusOrderById(ACTIVE).stream().map(LoanProductDto::of).toList();
    }

    public List<CrossSellProductDto> activeCrossSells() {
        return crossSells.findByStatusOrderById(ACTIVE).stream().map(CrossSellProductDto::of).toList();
    }

    public List<BranchDto> activeBranches() {
        return branches.findByStatusOrderById(ACTIVE).stream().map(BranchDto::of).toList();
    }

    /** Mã gói lạ trả danh sách rỗng (mock cũng vậy). */
    public List<ChecklistItem> checklist(String productCode) {
        return productDocs.findRulesByProductCode(productCode).stream().map(row -> {
            DocumentType t = (DocumentType) row[0];
            String name = t.getId() == DocumentType.SYSTEM_DOC_TYPE ? t.getName() + " – hệ thống tự lập" : t.getName();
            return new ChecklistItem(name, t.getGroup(), (Boolean) row[1]);
        }).toList();
    }
}
