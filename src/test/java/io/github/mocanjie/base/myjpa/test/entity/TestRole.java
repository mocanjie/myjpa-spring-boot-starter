package io.github.mocanjie.base.myjpa.test.entity;

import io.github.mocanjie.base.myjpa.MyTableEntity;
import io.github.mocanjie.base.myjpa.annotation.MyTable;

@MyTable(value = "role", delColumn = "is_deleted", delField = "isDeleted", delValue = 1)
public class TestRole implements MyTableEntity {
    private Long id;
    private String roleName;
    private Integer isDeleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}
