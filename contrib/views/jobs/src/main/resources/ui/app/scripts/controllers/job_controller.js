/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

App.JobController = Ember.ObjectController.extend({

  name: 'jobController',

  loaded: false,

  loadTimeout: null,

  job: null,

  sortingColumn: null,

  showPopupButtons: [
    Ember.Object.create({title: Em.I18n.t('ok'), dismiss: 'modal'})
  ],

  showPopup: function (title) {
    Bootstrap.ModalManager.open(
      'errorPopup',
      title,
      'job/error_popup',
      this.get('showPopupButtons'),
      this
    );
  },

  loadJobDetails: function () {
    var self = this,
      timeout = this.get('loadTimeout'),
      yarnService = App.HiveJob.store.getById('service', 'YARN'),
      content = this.get('content');
    if (!Em.isNone(yarnService)) {
      if (!Em.isNone(content)) {
        App.Helpers.jobs.refreshJobDetails(
          content,
          function () {
            self.set('content', App.HiveJob.store.getById('hiveJob', content.get('id')));
            self.set('loaded', true);
          },
          function (errorId) {
            switch (errorId) {
              case 'job.dag.noId':
                self.set('error_message', Em.I18n.t('jobs.hive.tez.dag.error.noDagId.message'));
                self.showPopup(Em.I18n.t('jobs.hive.tez.dag.error.noDagId.title'));
                break;
              case 'job.dag.noname':
                self.set('error_message', Em.I18n.t('jobs.hive.tez.dag.error.noDag.message'));
                self.showPopup(Em.I18n.t('jobs.hive.tez.dag.error.noDag.title'));
                break;
              case 'job.dag.id.noDag':
                self.set('error_message', Em.I18n.t('jobs.hive.tez.dag.error.noDagForId.message'));
                self.showPopup(Em.I18n.t('jobs.hive.tez.dag.error.noDagForId.title'));
                break;
              case 'job.dag.id.loaderror':
              case 'job.dag.name.loaderror':
                break;
              default:
                break;
            }
            self.routeToJobs();
          }
        );
      }
    } else {
      clearTimeout(timeout);
      timeout = setTimeout(function () {
        self.loadJobDetails();
      }, 300);
    }
  },

  /**
   * open jobs page
   * @method routeToJobs
   */
  routeToJobs: function () {
    this.transitionToRoute('jobs');
  },

  actions: {
    actionRouteToJobs: function () {
      this.routeToJobs();
    }
  }

});