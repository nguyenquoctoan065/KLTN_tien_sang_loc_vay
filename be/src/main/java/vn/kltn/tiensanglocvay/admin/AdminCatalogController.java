package vn.kltn.tiensanglocvay.admin;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.admin.AdminDtos.AdminCatalog;
import vn.kltn.tiensanglocvay.admin.AdminDtos.CrossSellProductInput;
import vn.kltn.tiensanglocvay.admin.AdminDtos.DocumentTypeDto;
import vn.kltn.tiensanglocvay.admin.AdminDtos.DocumentTypeInput;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ProductDocRule;
import vn.kltn.tiensanglocvay.admin.AdminDtos.UpdateProductRequest;
import vn.kltn.tiensanglocvay.catalog.CrossSellProductDto;
import vn.kltn.tiensanglocvay.catalog.LoanProductDto;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.RequireRole;
import vn.kltn.tiensanglocvay.security.Role;

/** 7.3 /admin/catalog, /admin/products, /admin/document-types, /admin/product-documents, /admin/cross-sell-products (x-roles ADMIN). */
@RestController
@RequireRole(Role.ADMIN)
public class AdminCatalogController {

    private final AdminCatalogService service;

    public AdminCatalogController(AdminCatalogService service) {
        this.service = service;
    }

    @GetMapping("/admin/catalog")
    AdminCatalog catalog() {
        return service.catalog();
    }

    @PutMapping("/admin/products/{id}")
    LoanProductDto updateProduct(@PathVariable int id, @RequestBody(required = false) UpdateProductRequest b,
                                 @AuthenticationPrincipal CurrentUser user) {
        return service.updateProduct(id, b, user);
    }

    @PostMapping("/admin/document-types")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentTypeDto createDocType(@RequestBody(required = false) DocumentTypeInput b, @AuthenticationPrincipal CurrentUser user) {
        return service.createDocType(b, user);
    }

    @PutMapping("/admin/document-types/{id}")
    DocumentTypeDto updateDocType(@PathVariable int id, @RequestBody(required = false) DocumentTypeInput b,
                                  @AuthenticationPrincipal CurrentUser user) {
        return service.updateDocType(id, b, user);
    }

    @PutMapping("/admin/product-documents")
    List<ProductDocRule> replaceRules(@RequestBody(required = false) List<ProductDocRule> b, @AuthenticationPrincipal CurrentUser user) {
        return service.replaceRules(b, user);
    }

    @PostMapping("/admin/cross-sell-products")
    @ResponseStatus(HttpStatus.CREATED)
    CrossSellProductDto createCrossSell(@RequestBody(required = false) CrossSellProductInput b, @AuthenticationPrincipal CurrentUser user) {
        return service.createCrossSell(b, user);
    }

    @PutMapping("/admin/cross-sell-products/{id}")
    CrossSellProductDto updateCrossSell(@PathVariable int id, @RequestBody(required = false) CrossSellProductInput b,
                                        @AuthenticationPrincipal CurrentUser user) {
        return service.updateCrossSell(id, b, user);
    }
}
