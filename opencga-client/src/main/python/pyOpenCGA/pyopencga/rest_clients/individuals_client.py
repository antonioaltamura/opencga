from pyopencga.rest_clients._parent_rest_clients import _ParentRestClient


class Individuals(_ParentRestClient):
    """
    This class contains methods for the 'Individuals' webservices
    Client version: 2.0.0
    PATH: /{apiVersion}/individuals
    """

    def __init__(self, configuration, token=None, login_handler=None, *args, **kwargs):
        _category = 'individuals'
        super(Individuals, self).__init__(configuration, _category, token, login_handler, *args, **kwargs)

    def update(self, individuals, data=None, **options):
        """
        Update some individual attributes.
        PATH: /{apiVersion}/individuals/{individuals}/update

        :param str individuals: Comma separated list of individual ids.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str samples_action: Action to be performed if the array of samples is being updated. Allowed values: ['ADD', 'SET', 'REMOVE']
        :param str annotation_sets_action: Action to be performed if the array of annotationSets is being updated. Allowed values: ['ADD', 'SET', 'REMOVE']
        :param bool inc_version: Create a new version of individual.
        :param bool update_sample_version: Update all the sample references from the individual to point to their latest versions.
        :param dict data: params.
        """

        return self._post('update', query_id=individuals, data=data, **options)

    def acl(self, individuals, **options):
        """
        Return the acl of the individual. If member is provided, it will only return the acl for the member.
        PATH: /{apiVersion}/individuals/{individuals}/acl

        :param str individuals: Comma separated list of individual names or ids up to a maximum of 100.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str member: User or group id.
        :param bool silent: Boolean to retrieve all possible entries that are queried for, false to raise an exception whenever one of the entries looked for cannot be shown for whichever reason.
        """

        return self._get('acl', query_id=individuals, **options)

    def update_annotations(self, individual, annotation_set, data=None, **options):
        """
        Update annotations from an annotationSet.
        PATH: /{apiVersion}/individuals/{individual}/annotationSets/{annotationSet}/annotations/update

        :param str individual: Individual ID or name.
        :param str study: study.
        :param str annotation_set: AnnotationSet id to be updated.
        :param str action: Action to be performed: ADD to add new annotations; REPLACE to replace the value of an already existing annotation; SET to set the new list of annotations removing any possible old annotations; REMOVE to remove some annotations; RESET to set some annotations to the default value configured in the corresponding variables of the VariableSet if any. Allowed values: ['ADD', 'SET', 'REMOVE', 'RESET', 'REPLACE']
        :param bool inc_version: Create a new version of individual.
        :param bool update_sample_version: Update all the sample references from the individual to point to their latest versions.
        :param dict data: Json containing the map of annotations when the action is ADD, SET or REPLACE, a json with only the key 'remove' containing the comma separated variables to be removed as a value when the action is REMOVE or a json with only the key 'reset' containing the comma separated variables that will be set to the default value when the action is RESET.
        """

        return self._post('annotations/update', query_id=individual, subcategory='annotationSets', second_query_id=annotation_set, data=data, **options)

    def update_acl(self, members, data=None, **options):
        """
        Update the set of permissions granted for the member.
        PATH: /{apiVersion}/individuals/acl/{members}/update

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str members: Comma separated list of user or group ids.
        :param dict data: JSON containing the parameters to update the permissions. If propagate flag is set to true, it will propagate the permissions defined to the samples that are associated to the matching individuals.
        """

        return self._post('update', query_id=members, data=data, **options)

    def aggregation_stats(self, **options):
        """
        Fetch catalog individual stats.
        PATH: /{apiVersion}/individuals/aggregationStats

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param bool has_father: Has father.
        :param bool has_mother: Has mother.
        :param str num_multiples: Number of multiples.
        :param str multiples_type: Multiples type.
        :param str sex: Sex.
        :param str karyotypic_sex: Karyotypic sex.
        :param str ethnicity: Ethnicity.
        :param str population: Population.
        :param str creation_year: Creation year.
        :param str creation_month: Creation month (JANUARY, FEBRUARY...).
        :param str creation_day: Creation day.
        :param str creation_day_of_week: Creation day of week (MONDAY, TUESDAY...).
        :param str status: Status.
        :param str life_status: Life status.
        :param str affectation_status: Affectation status.
        :param str phenotypes: Phenotypes.
        :param str num_samples: Number of samples.
        :param bool parental_consanguinity: Parental consanguinity.
        :param str release: Release.
        :param str version: Version.
        :param str annotation: Annotation, e.g: key1=value(,key2=value).
        :param bool default: Calculate default stats.
        :param str field: List of fields separated by semicolons, e.g.: studies;type. For nested fields use >>, e.g.: studies>>biotype;type;numSamples[0..10]:1.
        """

        return self._get('aggregationStats', **options)

    def create(self, data=None, **options):
        """
        Create individual.
        PATH: /{apiVersion}/individuals/create

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str samples: Comma separated list of sample ids to be associated to the created individual.
        :param dict data: JSON containing individual information.
        """

        return self._post('create', data=data, **options)

    def info(self, individuals, **options):
        """
        Get individual information.
        PATH: /{apiVersion}/individuals/{individuals}/info

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param bool flatten_annotations: Flatten the annotations?.
        :param str individuals: Comma separated list of individual names or ids up to a maximum of 100.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param int version: Individual version.
        :param bool deleted: Boolean to retrieve deleted individuals.
        """

        return self._get('info', query_id=individuals, **options)

    def search(self, **options):
        """
        Search for individuals.
        PATH: /{apiVersion}/individuals/search

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param int limit: Number of results to be returned.
        :param int skip: Number of results to skip.
        :param bool count: Get the total number of results matching the query. Deactivated by default.
        :param bool flatten_annotations: Flatten the annotations?.
        :param str study: Study [[user@]project:]study where study and project can be either the id or alias.
        :param str name: name.
        :param str father: father.
        :param str mother: mother.
        :param str samples: Comma separated list sample IDs or UUIDs up to a maximum of 100.
        :param str sex: sex.
        :param str ethnicity: ethnicity.
        :param str disorders: Comma separated list of disorder ids or names.
        :param str population.name: Population name.
        :param str population.subpopulation: Subpopulation name.
        :param str population.description: Population description.
        :param str phenotypes: Comma separated list of phenotype ids or names.
        :param str karyotypic_sex: Karyotypic sex.
        :param str life_status: Life status.
        :param str affectation_status: Affectation status.
        :param bool deleted: Boolean to retrieve deleted individuals.
        :param str creation_date: Creation date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
        :param str modification_date: Modification date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
        :param str annotationset_name: DEPRECATED: Use annotation queryParam this way: annotationSet[=|==|!|!=]{annotationSetName}.
        :param str variable_set: DEPRECATED: Use annotation queryParam this way: variableSet[=|==|!|!=]{variableSetId}.
        :param str annotation: Annotation, e.g: key1=value(,key2=value).
        :param str release: Release value (Current release from the moment the individuals were first created).
        :param int snapshot: Snapshot value (Latest version of individuals in the specified release).
        """

        return self._get('search', **options)

    def delete(self, individuals, **options):
        """
        Delete existing individuals.
        PATH: /{apiVersion}/individuals/{individuals}/delete

        :param bool force: Force the deletion of individuals that already belong to families.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str individuals: Comma separated list of individual ids.
        """

        return self._delete('delete', query_id=individuals, **options)

