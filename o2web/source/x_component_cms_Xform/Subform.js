MWF.xDesktop.requireApp("process.Xform", "Subform", null, false);
MWF.xApplication.cms.Xform.Subform = MWF.CMSSubform =  new Class({
    Extends: MWF.APPSubform,

    getSubform: function(callback){
        if (this.json.subformType==="script"){
            if (this.json.subformScript && this.json.subformScript.code){
                var data = this.form.Macro.exec(this.json.subformScript.code, this);
                if (data){
                    var formName, app;
                    if( typeOf( data ) === "string" ){
                        formName = data;
                    }else{
                        if( data.application )app = data.application;
                        if( data.subform )formName = data.subform;
                    }
                    if( formName ){
                        if( !app )app = this.form.businessData.document.appId || this.form.businessData.document.application;
                        MWF.Actions.get("x_cms_assemble_control").getFormWithColumn(formName, app, function(json){
                            this.getSubformData(json.data);
                            if (callback) callback();
                        }.bind(this));
                    }else{
                        if (callback) callback();
                    }
                }else{
                    if (callback) callback();
                }
            }
        }else{
            if (this.json.subformSelected && this.json.subformSelected!=="none"){
                var subformData = (this.form.app.relatedFormMap) ? this.form.app.relatedFormMap[this.json.subformSelected] : null;
                if (subformData) {
                    this.getSubformData({"data": subformData.data});
                    if (callback) callback();
                } else {
                    var app;
                    if( this.json.subformAppSelected ){
                        app = this.json.subformAppSelected;
                    }else{
                        app = this.form.businessData.document.appId || this.form.businessData.document.application;
                    }
                    MWF.Actions.get("x_cms_assemble_control").getFormWithColumn(this.json.subformSelected, app, function(json){
                        this.getSubformData(json.data);
                        if (callback) callback();
                    }.bind(this));
                }
            }else{
                if (callback) callback();
            }
        }

        //if (this.json.subformType==="script"){
        //    if (this.json.subformScript.code){
        //        var formNome = this.form.Macro.exec(this.json.subformScript.code, this);
        //        if (formNome){
        //            MWF.Actions.get("x_cms_assemble_control").getForm(formNome, function(json){
        //                this.getSubformData(json.data);
        //                if (callback) callback();
        //            }.bind(this));
        //        }
        //    }
        //}else{
        //    if (this.json.subformSelected && this.json.subformSelected!=="none"){
        //        MWF.Actions.get("x_cms_assemble_control").getForm(this.json.subformSelected, function(json){
        //            this.getSubformData(json.data);
        //            if (callback) callback();
        //        }.bind(this));
        //    }else{
        //        if (callback) callback();
        //    }
        //}
    },
    getSubformData: function (data) {
        if (!data || typeOf(data) !== "object") return;
        var subformDataStr = null;
        if ( this.form.json.mode !== "Mobile" && !layout.mobile){
            subformDataStr = data.data;
        }else{
            subformDataStr = data.mobileData || data.data;
        }
        this.subformData = null;
        if (subformDataStr) {
            if( this.form.isParseLanguage ) {
                var jsonStr = o2.bindJson(MWF.decodeJsonString(subformDataStr), {"lp": MWF.xApplication.process.Xform.LP.form});
                this.subformData = JSON.decode(jsonStr);
            }else{
                this.subformData = JSON.decode(MWF.decodeJsonString(subformDataStr));
            }
            this.subformData.updateTime = data.updateTime;
        }
    }
});
