package io.hwan.atlaskb.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_vectors")
public class DocumentVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vector_id")
    private Long vectorId;

    @Column(name = "file_md5", nullable = false)
    private String fileMd5;

    @Column(name = "chunk_id", nullable = false)
    private Integer chunkId;

    @Column(name = "text_content")
    private String textContent;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "org_tag")
    private String orgTag;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    public Long getVectorId() {
        return vectorId;
    }

    public void setVectorId(Long vectorId) {
        this.vectorId = vectorId;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }

    public Integer getChunkId() {
        return chunkId;
    }

    public void setChunkId(Integer chunkId) {
        this.chunkId = chunkId;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrgTag() {
        return orgTag;
    }

    public void setOrgTag(String orgTag) {
        this.orgTag = orgTag;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }
}
