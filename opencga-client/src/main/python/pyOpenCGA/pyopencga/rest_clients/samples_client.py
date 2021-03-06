from pyopencga.rest_clients._parent_rest_clients import _ParentRestClient


class Samples(_ParentRestClient):
    """
    This class contains methods for the 'Samples' webservices
    Client version: 2.0.0
    PATH: /{apiVersion}/samples
    """

    def __init__(self, configuration, token=None, login_handler=None, *args, **kwargs):
        _category = 'samples'
        super(Samples, self).__init__(configuration, _category, token, login_handler, *args, **kwargs)

    def info(self, samples, **options):
        """
        Get sample information.
        PATH: /{apiVersion}/samples/{samples}/info

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param bool include_individual: Include Individual object as an attribute (this replaces old lazy parameter).
        :param bool flatten_annotations: Flatten the annotations?.
        :param str samples: Comma separated list sample IDs or UUIDs up to a maximum of 100.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param int version: Sample version.
        :param bool deleted: Boolean to retrieve deleted samples.
        """

        return self._get('info', query_id=samples, **options)

    def create(self, data=None, **options):
        """
        Create sample.
        PATH: /{apiVersion}/samples/create

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str individual: DEPRECATED: It should be passed in the body.
        :param dict data: JSON containing sample information.
        """

        return self._post('create', data=data, **options)

    def load(self, file, **options):
        """
        Load samples from a ped file [EXPERIMENTAL].
        PATH: /{apiVersion}/samples/load

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str file: file.
        :param str variable_set: variableSet.
        """

        options['file'] = file
        return self._get('load', **options)

    def update(self, samples, data=None, **options):
        """
        Update some sample attributes.
        PATH: /{apiVersion}/samples/{samples}/update

        :param str samples: Comma separated list sample IDs or UUIDs up to a maximum of 100.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param bool inc_version: Create a new version of sample.
        :param str annotation_sets_action: Action to be performed if the array of annotationSets is being updated. Allowed values: ['ADD', 'SET', 'REMOVE']
        :param dict data: params.
        """

        return self._post('update', query_id=samples, data=data, **options)

    def acl(self, samples, **options):
        """
        Returns the acl of the samples. If member is provided, it will only return the acl for the member.
        PATH: /{apiVersion}/samples/{samples}/acl

        :param str samples: Comma separated list sample IDs or UUIDs up to a maximum of 100.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str member: User or group id.
        :param bool silent: Boolean to retrieve all possible entries that are queried for, false to raise an exception whenever one of the entries looked for cannot be shown for whichever reason.
        """

        return self._get('acl', query_id=samples, **options)

    def update_annotations(self, sample, annotation_set, data=None, **options):
        """
        Update annotations from an annotationSet.
        PATH: /{apiVersion}/samples/{sample}/annotationSets/{annotationSet}/annotations/update

        :param str sample: Sample id.
        :param str study: study.
        :param str annotation_set: AnnotationSet id to be updated.
        :param str action: Action to be performed: ADD to add new annotations; REPLACE to replace the value of an already existing annotation; SET to set the new list of annotations removing any possible old annotations; REMOVE to remove some annotations; RESET to set some annotations to the default value configured in the corresponding variables of the VariableSet if any. Allowed values: ['ADD', 'SET', 'REMOVE', 'RESET', 'REPLACE']
        :param bool inc_version: Create a new version of sample.
        :param dict data: Json containing the map of annotations when the action is ADD, SET or REPLACE, a json with only the key 'remove' containing the comma separated variables to be removed as a value when the action is REMOVE or a json with only the key 'reset' containing the comma separated variables that will be set to the default value when the action is RESET.
        """

        return self._post('annotations/update', query_id=sample, subcategory='annotationSets', second_query_id=annotation_set, data=data, **options)

    def update_acl(self, members, data=None, **options):
        """
        Update the set of permissions granted for the member.
        PATH: /{apiVersion}/samples/acl/{members}/update

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str members: Comma separated list of user or group ids.
        :param dict data: JSON containing the parameters to update the permissions. If propagate flag is set to true, it will propagate the permissions defined to the individuals that are associated to the matching samples.
        """

        return self._post('update', query_id=members, data=data, **options)

    def aggregation_stats(self, **options):
        """
        Fetch catalog sample stats.
        PATH: /{apiVersion}/samples/aggregationStats

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str source: Source.
        :param str creation_year: Creation year.
        :param str creation_month: Creation month (JANUARY, FEBRUARY...).
        :param str creation_day: Creation day.
        :param str creation_day_of_week: Creation day of week (MONDAY, TUESDAY...).
        :param str status: Status.
        :param str type: Type.
        :param str phenotypes: Phenotypes.
        :param str release: Release.
        :param str version: Version.
        :param bool somatic: Somatic.
        :param str annotation: Annotation, e.g: key1=value(,key2=value).
        :param bool default: Calculate default stats.
        :param str field: List of fields separated by semicolons, e.g.: studies;type. For nested fields use >>, e.g.: studies>>biotype;type;numSamples[0..10]:1.
        """

        return self._get('aggregationStats', **options)

    def delete(self, samples, **options):
        """
        Delete samples.
        PATH: /{apiVersion}/samples/{samples}/delete

        :param bool force: Force the deletion of samples even if they are associated to files, individuals or cohorts.
        :param str empty_files_action: Action to be performed over files that were associated only to the sample to be deleted. Possible actions are NONE, TRASH, DELETE.
        :param bool delete_empty_cohorts: Boolean indicating if the cohorts associated only to the sample to be deleted should be also deleted.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str samples: Comma separated list sample IDs or UUIDs up to a maximum of 100.
        """

        return self._delete('delete', query_id=samples, **options)

    def search(self, **options):
        """
        Sample search method.
        PATH: /{apiVersion}/samples/search

        :param str include: Fields included in the response, whole JSON path must be provided.
        :param str exclude: Fields excluded in the response, whole JSON path must be provided.
        :param int limit: Number of results to be returned.
        :param int skip: Number of results to skip.
        :param bool count: Get the total number of results matching the query. Deactivated by default.
        :param bool include_individual: Include Individual object as an attribute (this replaces old lazy parameter).
        :param bool flatten_annotations: Flatten the annotations?.
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID.
        :param str name: DEPRECATED: name.
        :param str source: source.
        :param str type: type.
        :param bool somatic: somatic.
        :param str individual: Individual ID or name.
        :param str creation_date: Creation date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
        :param str modification_date: Modification date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805.
        :param bool deleted: Boolean to retrieve deleted samples.
        :param str phenotypes: Comma separated list of phenotype ids or names.
        :param str annotationset_name: DEPRECATED: Use annotation queryParam this way: annotationSet[=|==|!|!=]{annotationSetName}.
        :param str variable_set: DEPRECATED: Use annotation queryParam this way: variableSet[=|==|!|!=]{variableSetId}.
        :param str annotation: Annotation, e.g: key1=value(,key2=value).
        :param str attributes: Text attributes (Format: sex=male,age>20 ...).
        :param str nattributes: Numerical attributes (Format: sex=male,age>20 ...).
        :param str release: Release value (Current release from the moment the samples were first created).
        :param int snapshot: Snapshot value (Latest version of samples in the specified release).
        """

        return self._get('search', **options)

