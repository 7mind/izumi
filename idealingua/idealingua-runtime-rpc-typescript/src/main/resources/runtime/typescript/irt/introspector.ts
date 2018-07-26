
export enum IntrospectorTypes {
    // User types
    Enum,
    Mixin,
    Data,
    Adt,
    Id,
    Service,
    Method,

    // Generic types
    List,
    Map,
    Opt,
    Set,

    // Primitive types
    Str,
    Bool,
    I08,
    I16,
    I32,
    I64,
    U08,
    U16,
    U32,
    U64,
    F32,
    F64,
    Uid,
    Tsz,
    Tsl,
    Tsu,
    Time,
    Date
}

export interface IIntrospectorUserType extends IIntrospectorType {
    full: string;
}

export interface IIntrospectorGenericType extends IIntrospectorType {
    value: IIntrospectorType;
}

export interface IIntrospectorMapType extends IIntrospectorGenericType {
    key: IIntrospectorType;
}

export interface IIntrospectorType {
    intro: IntrospectorTypes;
}

export interface IIntrospectorObjectField {
    name: string;
    accessName: string;
    type: IIntrospectorType;
}

export interface IIntrospectorObjectAdtMember {
    name: string;
    type: IIntrospectorType;
}

export interface IIntrospectorMixinObject extends IIntrospectorObjectWithFields {
    implementations: () => string[];
}

export interface IIntrospectorDataObject extends IIntrospectorObjectWithFields {
}

export interface IIntrospectorIdObject extends IIntrospectorObjectWithFields {
}

export interface IIntrospectorObjectWithFields extends IIntrospectorObject {
    ctor: () => any;
    fields: IIntrospectorObjectField[];
}

export interface IIntrospectorEnumObject extends IIntrospectorObject {
    options: any[];
}

export interface IIntrospectorAdtObject extends IIntrospectorObject {
    options: IIntrospectorObjectAdtMember[];
}

export interface IIntrospectorObject {
    full: string;
    short: string;
    package: string;
    type: IntrospectorTypes;
}

export class Introspector {
    private static knownTypesMap: {[key: string]: IIntrospectorObject} = {};
    private static knownTypesList: IIntrospectorObject[] = [];

    public static register(fullClassName: string, intro: IIntrospectorObject) {
        switch (intro.type) {
            case IntrospectorTypes.Adt: {
                if (!Array.isArray((intro as IIntrospectorAdtObject).options)) {
                    throw new Error("Registering an Adt requires a list of options.");
                }
            } break;

            case IntrospectorTypes.Id:
            case IntrospectorTypes.Mixin:
            case IntrospectorTypes.Data: {
                if (!Array.isArray((intro as IIntrospectorObjectWithFields).fields)) {
                    throw new Error("Registering a Data / Mixin / Id requires a list of options.");
                }

                if (typeof (intro as IIntrospectorObjectWithFields).ctor !== 'function') {
                    throw new Error("Registering a Data / Mixin / Id requires a valid ctor to be present.");
                }
            } break;

            case IntrospectorTypes.Enum: {
                if (!Array.isArray((intro as IIntrospectorEnumObject).options)) {
                    throw new Error("Registering an Enum requires a list of options.");
                }
            } break;

            case IntrospectorTypes.Service: {

            } break;

            default: {
                throw new Error("Trying to register an unsupported type for introspection: " + intro);
            }
        }

        Introspector.knownTypesMap[fullClassName] = intro;
        Introspector.knownTypesList.push(intro);
    }

    public static find(fullClassName: string): IIntrospectorObject {
        return Introspector.knownTypesMap[fullClassName];
    }

    public static all(): IIntrospectorObject[] {
        return Introspector.knownTypesList;
    }
}