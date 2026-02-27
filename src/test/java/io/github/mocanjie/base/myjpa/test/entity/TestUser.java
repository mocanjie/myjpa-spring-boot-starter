package io.github.mocanjie.base.myjpa.test.entity;

import io.github.mocanjie.base.myjpa.annotation.MyTable;

@MyTable(value = "user", delColumn = "delete_flag", delField = "deleteFlag", delValue = 1)
public class TestUser {
    private Long id;
    private String username;
    private Integer deleteFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(Integer deleteFlag) { this.deleteFlag = deleteFlag; }
}
