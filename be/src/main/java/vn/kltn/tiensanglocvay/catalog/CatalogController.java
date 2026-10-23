package vn.kltn.tiensanglocvay.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Danh mục công khai (không cần đăng nhập): /products, /products/{code}/checklist, /cross-sell-products, /branches. */
@RestController
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    public List<LoanProductDto> products() {
        return catalog.activeProducts();
    }

    @GetMapping("/products/{code}/checklist")
    public List<ChecklistItem> checklist(@PathVariable String code) {
        return catalog.checklist(code);
    }

    @GetMapping("/cross-sell-products")
    public List<CrossSellProductDto> crossSells() {
        return catalog.activeCrossSells();
    }

    @GetMapping("/branches")
    public List<BranchDto> branches() {
        return catalog.activeBranches();
    }
}
