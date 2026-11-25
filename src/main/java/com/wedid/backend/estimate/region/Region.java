
package com.wedid.backend.estimate.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "regions", schema = "public")
public class Region {

    @Id
    private Short id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    protected Region() {
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Short getSortOrder() {
        return sortOrder;
    }
}
