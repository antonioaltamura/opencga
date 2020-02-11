import argparse
import sys
import textwrap

from rest_client_generator import RestClientGenerator


class JavaScriptClientGenerator(RestClientGenerator):

    def __init__(self, server_url, output_dir):
        super().__init__(server_url, output_dir)

        self.TEXT_WRAP = 134  # 140 columns max (6 cols for indentation)

        self.CATEGORIES = {
            'Users': 'Users',
            'Projects': 'Projects',
            'Studies': 'Studies',
            'Files': 'Files',
            'Jobs': 'Jobs',
            'Samples': 'Samples',
            'Individuals': 'Individuals',
            'Families': 'Families',
            'Cohorts': 'Cohorts',
            'Disease Panels': 'Panels',
            'Analysis - Alignment': 'Alignment',
            'Analysis - Variant': 'Variant',
            'Analysis - Clinical Interpretation': 'Clinical',
            'Operations - Variant Storage': 'VariantOperations',
            'Meta': 'Meta',
            'GA4GH': 'GA4GH',
            'Admin': 'Admin'
        }

        self.PARAMS_TYPE = {
            'string': 'String',
            'integer': 'Number',
            'int': 'Number',
            'object': 'Object',
            'list': 'Object',
            'boolean': 'Boolean',
            'enum': 'String'
        }

    def get_imports(self):
        return (f'/**\n'
                f' * Copyright 2015-2020 OpenCB\n'
                f' * Licensed under the Apache License, Version 2.0 (the "License");\n'
                f' * you may not use this file except in compliance with the License.\n'
                f' * You may obtain a copy of the License at\n'
                f' * http://www.apache.org/licenses/LICENSE-2.0\n'
                f' * Unless required by applicable law or agreed to in writing, software\n'
                f' * distributed under the License is distributed on an "AS IS" BASIS,\n'
                f' * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n'
                f' * See the License for the specific language governing permissions and\n'
                f' * limitations under the License.\n'
                f' **/\n\n'
                f'import OpenCGAParentClass from "./OpenCGAParentClass.js"\n\n')

    def get_class_definition(self, category):
        name = self.CATEGORIES[self.get_category_name(category)]
        return (f'/**\n * This class contains the methods for the "{name}" resource\n */\n\n'
                f'class {name} extends OpenCGAParentClass {{\n\n'
                f'    constructor(config) {{\n'
                f'        super(config);\n'
                f'    }}\n')

    def get_class_end(self):
        return "}"

    # The params_object stands for the query string of the HTTP [GET] Request (excluding mandatory params).
    def has_params_object(self, endpoint):
        path_params = self.get_path_params(endpoint)
        # get_optional_parameters() doesn't filter out path_params
        return any([name for name in self.get_optional_parameters(endpoint) if name not in path_params])

    def has_body(self):
        return "data" in self.parameters

    def get_method_doc(self, endpoint):
        description = self.text_wrap(endpoint["description"], '\n    * ')
        path_params = []
        mandatory_params = []
        params_props = []

        _path_params = self.get_path_params(endpoint)
        _mandatory_query_params = self.get_mandatory_query_params(endpoint)

        for name, param in self.parameters.items():
            param_name = name + " = \"" + param["defaultValue"] + "\"" if param["defaultValue"] else name
            param_type = "|".join([f'"{p}"' for p in param["allowedValues"].split(",")]) if param["allowedValues"] else self.PARAMS_TYPE[param["type"]]
            param_description = f'{self.get_parameter_description(name)} {"The default value is " + param["defaultValue"] + "." if param["defaultValue"] else ""}'
            if name in _path_params or name == "data":
                path_params.append(f'@param {{{param_type}}} {"[" + param_name + "]" if not self.is_required(name) else param_name} - {param_description}')
            elif name in _mandatory_query_params:
                mandatory_params.append(f'@param {{{param_type}}} {param_name} - {param_description}')
            else:
                params_props.append(f'@param {{{param_type}}} {"[params." + param_name + "]"} - {param_description}')
        if params_props:
            params_props.insert(0, f'@param {{Object}} [params] - The Object containing the following optional parameters')

        # text wrapping
        params = "\n    * ".join(self.text_wrap(line, "\n    *     ") for line in path_params + mandatory_params + params_props)

        return (f'    /** {description}\n    * '
                f'{params}\n    * '
                f'@returns {{Promise}} Promise object in the form of RestResponse instance\n'
                f'    */\n')

    def get_method_args(self, endpoint):
        args = [self.get_endpoint_id1(), self.get_endpoint_id2(), ", ".join(self.get_mandatory_query_params(endpoint)),
                "data", "params"]
        mask = [self.get_endpoint_id1(), self.get_endpoint_id2(), len(self.get_mandatory_query_params(endpoint)),
                self.has_body(), self.has_params_object(endpoint)]
        return ", ".join(p for p, m in zip(args, mask) if m)

    def get_method_definition(self, category, endpoint):
        # wraps mandatory and optional params in one js Object
        query_string_params = False
        mandatory_params = self.get_mandatory_query_params(endpoint)
        if self.has_params_object(endpoint):
            if len(mandatory_params) > 0:
                query_string_params = f'{{{", ".join(mandatory_params) if len(mandatory_params) > 1 else mandatory_params[0]}, ...params}}'
            else:
                query_string_params = f'params'
        else:
            if len(mandatory_params) > 0:
                query_string_params = f'{ "{{" + ", ".join(mandatory_params) + "}}" if len(mandatory_params) > 1 else mandatory_params[0]}'

        endpoint_method_args = ", ".join(s for s in [
            f'"{self.get_endpoint_category()}"',
            self.get_endpoint_id1() if self.get_endpoint_id1() else "null",
            f'"{self.get_endpoint_subcategory()}"' if self.subcategory else "null",
            self.get_endpoint_id2() if self.get_endpoint_id2() else "null",
            f'"{self.get_endpoint_action()}"' if self.get_endpoint_action() else "null",
            "data" if self.has_body() else False,
            query_string_params
        ] if s)
        return (f' {self.get_method_doc(endpoint)}'
                f'    {self.camelCase(self.get_method_name(endpoint, category))}({self.get_method_args(endpoint)}) {{\n'
                f'        return this._{self.get_endpoint_method(endpoint).lower()}({endpoint_method_args});\n'
                f'    }}\n')

    def get_file_name(self, category):
        return self.CATEGORIES[self.get_category_name(category)] + ".js"

    def text_wrap(self, string, separator):
        w = textwrap.TextWrapper(width=self.TEXT_WRAP, break_long_words=True, replace_whitespace=False)
        return separator.join(w.wrap(string))

    def camelCase(self, st):
        output = ''.join(x for x in st.title() if x.isalnum())
        return output[0].lower() + output[1:]


def _setup_argparse():
    desc = 'This script creates automatically all RestClients files'
    parser = argparse.ArgumentParser(description=desc, formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('server_url', help='server URL')
    parser.add_argument('output_dir', help='output directory')
    args = parser.parse_args()
    return args


def main():
    args = _setup_argparse()
    JavaScriptClientGenerator(args.server_url, args.output_dir).create_rest_clients()


if __name__ == '__main__':
    sys.exit(main())
