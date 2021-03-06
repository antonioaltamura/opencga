from pyopencga.rest_clients._parent_rest_clients import _ParentRestClient


class Admin(_ParentRestClient):
    """
    This class contains methods for the 'Admin' webservices
    Client version: 2.0.0
    PATH: /{apiVersion}/admin
    """

    def __init__(self, configuration, token=None, login_handler=None, *args, **kwargs):
        _category = 'admin'
        super(Admin, self).__init__(configuration, _category, token, login_handler, *args, **kwargs)

    def index_stats_catalog(self, **options):
        """
        Sync Catalog into the Solr.
        PATH: /{apiVersion}/admin/catalog/indexStats
        """

        return self._post('indexStats', subcategory='catalog', **options)

    def panel_catalog(self, data=None, **options):
        """
        Handle global panels.
        PATH: /{apiVersion}/admin/catalog/panel

        :param bool panel_app: Import panels from PanelApp (GEL).
        :param bool overwrite: Flag indicating to overwrite installed panels in case of an ID conflict.
        :param str delete: Comma separated list of global panel ids to delete.
        :param dict data: Panel parameters to be installed.
        """

        return self._post('panel', subcategory='catalog', data=data, **options)

    def jwt_catalog(self, data=None, **options):
        """
        Change JWT secret key.
        PATH: /{apiVersion}/admin/catalog/jwt

        :param dict data: JSON containing the parameters.
        """

        return self._post('jwt', subcategory='catalog', data=data, **options)

    def install_catalog(self, data=None, **options):
        """
        Install OpenCGA database.
        PATH: /{apiVersion}/admin/catalog/install

        :param dict data: JSON containing the mandatory parameters.
        """

        return self._post('install', subcategory='catalog', data=data, **options)

    def import_users(self, data=None, **options):
        """
        Import users or a group of users from LDAP or AAD.
        PATH: /{apiVersion}/admin/users/import

        :param dict data: JSON containing the parameters.
        """

        return self._post('import', subcategory='users', data=data, **options)

    def sync_users(self, data=None, **options):
        """
        Synchronise groups of users with LDAP groups.
        PATH: /{apiVersion}/admin/users/sync

        :param dict data: JSON containing the parameters.
        """

        return self._post('sync', subcategory='users', data=data, **options)

    def group_by_audit(self, fields, entity, **options):
        """
        Group by operation.
        PATH: /{apiVersion}/admin/audit/groupBy

        :param bool count: Count the number of elements matching the group.
        :param int limit: Maximum number of documents (groups) to be returned.
        :param str fields: Comma separated list of fields by which to group by.
        :param str entity: Entity to be grouped by.
        :param str action: Action performed.
        :param str before: Object before update.
        :param str after: Object after update.
        :param str date: Date <,<=,>,>=(Format: yyyyMMddHHmmss) and yyyyMMddHHmmss-yyyyMMddHHmmss.
        """

        options['fields'] = fields
        options['entity'] = entity
        return self._get('groupBy', subcategory='audit', **options)

    def create_users(self, data=None, **options):
        """
        Create a new user.
        PATH: /{apiVersion}/admin/users/create

        :param dict data: JSON containing the parameters.
        """

        return self._post('create', subcategory='users', data=data, **options)

