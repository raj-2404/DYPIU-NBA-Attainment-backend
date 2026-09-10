-- Create Programme Batch Indirect Assessments Table for multi-source surveys and co-curricular events

CREATE TABLE IF NOT EXISTS programme_batch_indirect_assessments (
    id VARCHAR(255) PRIMARY KEY,
    programme_batch_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'EVENT',
    description TEXT,
    scores_json TEXT,
    created_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pbia_programme_batch FOREIGN KEY (programme_batch_id) REFERENCES programme_batches(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pbia_batch_id ON programme_batch_indirect_assessments (programme_batch_id);
