package com.pokerlab.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runtime provenance is separate from the unchanged historical public lesson protocol. */
@RestController
@ConditionalOnProperty(name = "pokerlab.trainer.root-hand-bank-enabled", havingValue = "true")
public class RootHandBankStartupController {
    private final RootHandBankStartupSource source;

    public RootHandBankStartupController(RootHandBankStartupSource source) {
        this.source = source;
    }

    @GetMapping("/api/v1/trainer/research/root-hand-bank-drill/startup")
    public ResponseEntity<RootHandBankStartupSource.Provenance> startup() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(source.provenance());
    }
}
