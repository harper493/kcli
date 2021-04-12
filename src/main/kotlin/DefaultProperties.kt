val defaultProperties = """

rename.collection.access_control_lists               = acls

alternate.interface.disable                          = state=disabled
alternate.interface.enable                           = state=enabled

autoexpand.*.entries                                 = entry
autoexpand.configuration.policies                    = 
autoexpand.*.policies                                = policy

brief_name.*.rtt                                     = rtt
brief_name.*.mac                                     = mac_address
brief_name.*.descr                                   = description
brief_name.class.sd_application_host                 = host
brief_name.class.sd_host_application                 = application
brief_name.class.sd_geolocation_host                 = host
brief_name.class.sd_host_geolocation                 = geolocation
brief_name.class.sd_application_user                 = user
brief_name.class.sd_user_application                 = application
brief_name.class.host                                = host

suppress.*.name                                      = 1
suppress.*.hidden                                    = 1
suppress.*.class                                     = 1
suppress.*.timestamp                                 = 1
suppress.*._key_order                                = 1
suppress.*.color                                     = 1
suppress.administrator.password                      = 1
suppress.administrator.old_password                  = 1
suppress.administrator.client_preferences            = 1
suppress.base.parameters                             = 1
suppress.configuration.parameters                    = 1
suppress.configuration.ping_requests                 = 1
suppress.configuration.copy                          = 1
suppress.configuration.reload_system                 = 1
suppress.configuration.save_config                   = 1
suppress.configuration.text                          = 1
suppress.configuration.traceroutes                   = 1
suppress.flow.description                            = 1
suppress.pointer_class.description                   = 1

extra.configuration.terminal                         = 1

#
# Color overrides go here. They are of the form:
#
#color.red                                           = 88
#
# where the number is taken from the 8-bit color codes of the
# escape sequence standard, ISO 6429
# (see https://en.wikipedia.org/wiki/ANSI_escape_code )
#

parameter.column_underline                           = 1
parameter.page_size                                  = 40
parameter.page_size_no_tty                           = 1000
parameter.collection_completer_limit                 = 100
parameter.show_collection_max_field_width            = 40
parameter.show_object_max_field_width                = 60
parameter.markup                                     = 1
parameter.heading_color                              = magenta
parameter.result_color                               = deep_blue
parameter.login_color                                = mid_blue
parameter.heading_style                              = bold
parameter.show_columns                               = 3
parameter.label_column_width                         = 25
parameter.value_column_width                         = 30
parameter.parameter_columns                          = 2
parameter.parameter_label_width                      = 40
parameter.parameter_value_width                      = 25
parameter.metadata_column_width                      = 35
parameter.ping_timeout                               = 5

color.odd_row                                        = yucky_green
color.even_row                                       = yucky_brown
color.error                                          = red
color.show_heading                                   = magenta

help.mode_command.exit                               = Save changes and exit this object
help.base_command.exit                               = Exit the CLI
help.mode_command.quit                               = Exit this object without saving changes
help.base_command.quit                               = Exit the CLI
help.base_command.save                               = Save current configuration
help.reboot.current                                  = Reboot from current partition
help.reboot.alternate                                = Reboot from alternate partition
help.save.current                                    = Save configuration to current partition
help.save.alternate                                  = Save configuration to alternate partition
help.save.both                                       = Save configuration to both partitions
help.*.ascending                                     = Order objects by ascending attribute value
help.*.brief                                         = Show only most significant attributes
help.*.full                                          = Show all significant attributes
help.*.descending                                     = Order objects by descending attribute value (default)
help.*.detail                                        = Show all attributes
help.*.top                                           = Show highest objects by selected attribute
help.*.bottom                                        = Show lowest objects by selected attribute
help.*.by                                            = Select attribute to be used for ranking
help.*.select                                        = Choose attributes to be shown
help.*.only                                          = Show only selected attributes
help.*.with                                          = Filter objects based on attribute values

format.name_width                                    = 25
format.value_width                                   = 25
format.columns                                       = 2

replace.retransmission                               = ReTx
replace.retransmissions                              = ReTx
replace.retransmitted                                = ReTx
replace.destination                                  = Dest

hyphenate.application                                = appli-cation
hyphenate.beautiful                                  = beau-ti-ful
hyphenate.discarded                                  = disc-arded
hyphenate.downstream                                 = down-stream
hyphenate.interface                                  = inter-face
hyphenate.incredibly                                 = in-cred-ibly
hyphenate.operational                                = oper-ational
hyphenate.transmitted                                = trans-mitted

abbreviate.application                               = ap
abbreviate.system                                    = sys
abbreviate.geolocation                               = geo
abbreviate.user                                      = user
abbreviate.show                                      = sh
abbreviate.pointers                                  = poin

"""