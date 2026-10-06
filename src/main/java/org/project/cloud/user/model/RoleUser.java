package org.project.cloud.user.model;

import org.springframework.security.core.GrantedAuthority;

public enum RoleUser implements GrantedAuthority {
    USER, ADMIN;

public String getAuthority() {
    return "ROLE_" + this.name();
}

}
