package com.example.app.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    @Query(value = "SELECT NEXT VALUE FOR dbo.tenant_id_seq", nativeQuery = true)
    long nextId();
}
