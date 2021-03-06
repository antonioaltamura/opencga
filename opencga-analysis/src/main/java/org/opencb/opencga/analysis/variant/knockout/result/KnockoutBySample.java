package org.opencb.opencga.analysis.variant.knockout.result;

import org.opencb.opencga.core.models.individual.Individual;
import org.opencb.opencga.core.models.sample.Sample;

import java.util.*;

public class KnockoutBySample {

    private Individual individual;
    private Sample sample;

    private GeneKnockoutBySampleStats stats;

    private Map<String, KnockoutGene> genesMap = new HashMap<>();

    public Individual getIndividual() {
        return individual;
    }

    public KnockoutBySample setIndividual(Individual individual) {
        this.individual = individual;
        return this;
    }

    public Sample getSample() {
        return sample;
    }

    public KnockoutBySample setSample(Sample sample) {
        this.sample = sample;
        return this;
    }

    public GeneKnockoutBySampleStats getStats() {
        return stats;
    }

    public KnockoutBySample setStats(GeneKnockoutBySampleStats stats) {
        this.stats = stats;
        return this;
    }

    public Collection<KnockoutGene> getGenes() {
        return genesMap.values();
    }

    public KnockoutGene getGene(String gene) {
        return genesMap.computeIfAbsent(gene, KnockoutGene::new);
    }

    public KnockoutBySample setGenes(Collection<KnockoutGene> genes) {
        if (genes == null) {
            genesMap = null;
        } else {
            genesMap = new HashMap<>(genes.size());
            for (KnockoutGene gene : genes) {
                genesMap.put(gene.getName(), gene);
            }
        }
        return this;
    }

    public static class GeneKnockoutBySampleStats {
        private int numGenes;
        private int numTranscripts;
        private Map<KnockoutVariant.KnockoutType, Long> byType;

        public GeneKnockoutBySampleStats() {
            byType = new EnumMap<>(KnockoutVariant.KnockoutType.class);
        }

        public int getNumGenes() {
            return numGenes;
        }

        public GeneKnockoutBySampleStats setNumGenes(int numGenes) {
            this.numGenes = numGenes;
            return this;
        }

        public int getNumTranscripts() {
            return numTranscripts;
        }

        public GeneKnockoutBySampleStats setNumTranscripts(int numTranscripts) {
            this.numTranscripts = numTranscripts;
            return this;
        }

        public Map<KnockoutVariant.KnockoutType, Long> getByType() {
            return byType;
        }

        public GeneKnockoutBySampleStats setByType(Map<KnockoutVariant.KnockoutType, Long> byType) {
            this.byType = byType;
            return this;
        }
    }

    public static class KnockoutGene {
        private String id;
        private String name;
        private Map<String, KnockoutTranscript> transcriptsMap = new HashMap<>(); // Internal only

        public KnockoutGene() {
        }

        public KnockoutGene(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public KnockoutGene setId(String id) {
            this.id = id;
            return this;
        }

        public String getName() {
            return name;
        }

        public KnockoutGene setName(String name) {
            this.name = name;
            return this;
        }

        public KnockoutTranscript getTranscript(String transcript) {
            return transcriptsMap.computeIfAbsent(transcript, KnockoutTranscript::new);
        }

        public Collection<KnockoutTranscript> getTranscripts() {
            return transcriptsMap.values();
        }

        public KnockoutGene addTranscripts(Collection<KnockoutTranscript> transcripts) {
            for (KnockoutTranscript transcript : transcripts) {
                transcriptsMap.put(transcript.getId(), transcript);
            }
            return this;
        }

        public KnockoutGene setTranscripts(List<KnockoutTranscript> transcripts) {
            transcriptsMap.clear();
            if (transcripts != null) {
                transcripts.forEach(t -> transcriptsMap.put(t.getId(), t));
            }
            return this;
        }
    }

}
