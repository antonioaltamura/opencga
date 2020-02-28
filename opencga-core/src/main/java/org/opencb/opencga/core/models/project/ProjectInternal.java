package org.opencb.opencga.core.models.project;

import org.opencb.opencga.core.models.common.Status;

public class ProjectInternal {

    private DataStores datastores;
    private Status status;

    public ProjectInternal() {
    }

    public ProjectInternal(DataStores datastores, Status status) {
        this.datastores = datastores;
        this.status = status;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ProjectInternal{");
        sb.append("datastores=").append(datastores);
        sb.append(", status=").append(status);
        sb.append('}');
        return sb.toString();
    }

    public DataStores getDatastores() {
        return datastores;
    }

    public void setDatastores(DataStores datastores) {
        this.datastores = datastores;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
