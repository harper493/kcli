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
parameter.show_columns                               = 3
parameter.label_column_width                         = 25
parameter.value_column_width                         = 30
parameter.heading_style                              = bold
parameter.parameter_columns                          = 2
parameter.parameter_label_width                      = 40
parameter.parameter_value_width                      = 25
parameter.metadata_column_width                      = 35
parameter.ping_timeout                               = 5
parameter.help_key_width                             = 30
parameter.help_help_width                            = 80
parameter.history_limit                              = 40

color.odd_row                                        = yucky_green
color.even_row                                       = yucky_brown
color.error                                          = red
color.show_heading                                   = magenta
color.heading                                        = magenta
color.result                                         = deep_blue
color.login                                          = mid_blue
color.help                                           = mid_blue
color.help_key                                       = mid_blue
color.help_help                                      = deep_blue

help.no_help                                         = Sorry, no help available here
help.command.capture                                 = Control packet capture
help.command.columns                                 = Control table columns
help.command.count                                   = Count objects matching criteria
help.command.dump                                    = Dump diagnostic information
help.command.help                                    = Get help on specified command or topic
help.command.no                                      = Delete specified object
help.command.ping                                    = Ping specified address
help.command.quit                                    = Exit CLI
help.command.reboot                                  = Reboot STM
help.command.save                                    = Save current configuration
help.command.server                                  = Configure server information
help.command.set                                     = Set CLI attributes
help.command.show                                    = Show object properties and other things
help.command.shutdown                                = Shutdown STM
help.command.total                                   = Obtain totals and averages for specified objects and attributes
help.command.traceroute                              = Trace route to an address
help.reboot.current                                  = Reboot from current partition
help.reboot.alternate                                = Reboot from alternate partition
help.save.current                                    = Save configuration to current partition
help.save.alternate                                  = Save configuration to alternate partition
help.save.both                                       = Save configuration to both partitions
help.show.columns                                    = Show table column configuration
help.show.health                                     = Show system health report
help.show.license                                    = Show current STM license
help.show.metadata                                   = Show class metadata
help.show.parameters                                 = Show system parameters
help.show.pointers                                   = Show all pointers to object
help.show.servers                                    = Show configured STM servers
help.show.system                                     = Show top-level system attributes
help.show.version                                    = Show STM build version
help.*.brief                                         = Show only most significant attributes
help.*.full                                          = Show all important attributes
help.*.detail                                        = Show all attributes
help.*.debug                                         = Show all attributes including debug
help.*.top                                           = Show highest objects by selected attribute
help.*.bottom                                        = Show lowest objects by selected attribute
help.*.by                                            = Select attribute to be used for ranking
help.*.select                                        = Choose attributes to be shown
help.*.only                                          = Show only selected attributes
help.*.with                                          = Filter objects based on attribute values

help.help                                            = To see available commands, type <tab> at the command prompt. \
                                                       For help on a particular command, type 'help <command>'. \
                                                       Help is also available for the following topics: 
help.help.editing                                    = Command editing works like the shell. \nUse the left/right arrow keys \
                                                       to move around within the line, and the up/down arrow keys to \
                                                       recall previous commands.
help.help.show                                       = Show attributes (settings, counters, etc) of the specified \
                                                       object or objects. Use 'with' to filter which objects \
                                                       are shown, 'select' to select additional attributes, 'top' \
                                                       and 'bottom' to sort objects based on some attribute. For \
                                                       help on retrieving historical data, type 'help history'.

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
abbreviate.system                                    = sys
abbreviate.pointers                                  = poin

formatter.pointer_class.attribute_name               = display_attribute
formatter.pointer_class.pointer_object_class         = display_class
formatter.pointer_class.pointer_url                  = truncate_url


"""